package com.mysite.sbb.board.service;

import com.mysite.sbb.board.constant.IssueCategory;
import com.mysite.sbb.board.dto.IssueFilter;
import com.mysite.sbb.board.dto.IssueSummary;
import com.mysite.sbb.board.constant.BoardType;
import com.mysite.sbb.board.constant.IssueStatus;
import com.mysite.sbb.board.dto.PostForm;
import com.mysite.sbb.board.entity.Board;
import com.mysite.sbb.board.entity.IssueStatusHistory;
import com.mysite.sbb.board.entity.Post;
import com.mysite.sbb.board.entity.PostComment;
import com.mysite.sbb.board.repository.BoardRepository;
import com.mysite.sbb.board.repository.IssueStatusHistoryRepository;
import com.mysite.sbb.board.repository.PostCommentRepository;
import com.mysite.sbb.board.repository.PostRepository;
import com.mysite.sbb.common.entity.FileRef;
import com.mysite.sbb.common.exception.BusinessException;
import com.mysite.sbb.common.exception.NoAccessException;
import com.mysite.sbb.common.file.FileStore;
import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.member.service.MemberService;
import com.mysite.sbb.notification.constant.NotificationType;
import com.mysite.sbb.organization.service.OrganizationService;
import com.mysite.sbb.outbox.service.OutboxService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;

// 게시판 : 게시판 관리(관리자), 글 쓰기·고치기·지우기, 고정 공지, 이슈 상태, 댓글
@Service
@RequiredArgsConstructor
@Transactional
public class BoardService {

    private final BoardRepository boardRepository;
    private final PostRepository postRepository;
    private final PostCommentRepository postCommentRepository;
    private final IssueStatusHistoryRepository historyRepository;
    private final MemberService memberService;
    private final OrganizationService organizationService;
    private final FileStore fileStore;
    private final OutboxService outboxService;

    // ---------- 게시판 ----------

    @Transactional(readOnly = true)
    public List<Board> getBoards(Member me) {
        return boardRepository.findAllByOrderBySortOrderAscIdAsc().stream().filter(b -> b.canAccess(me)).toList();
    }

    @Transactional(readOnly = true)
    public List<Board> getAllBoards() {
        return boardRepository.findAllByOrderBySortOrderAscIdAsc();
    }

    @Transactional(readOnly = true)
    public Board getBoard(Long id, Member me) {
        Board b = boardRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("게시판을 찾을 수 없어요."));
        if (!b.canAccess(me)) throw new NoAccessException("이 게시판을 볼 권한이 없어요. 팀 게시판은 그 팀 직원만 볼 수 있어요.");
        return b;
    }

    @Transactional(readOnly = true)
    public long countPosts(Long boardId) {
        return postRepository.countByBoardId(boardId);
    }

    public Board createBoard(String name, String description, BoardType type, Long teamId) {
        if (name == null || name.isBlank()) throw new BusinessException("게시판 이름을 입력해 주세요.");
        return boardRepository.save(Board.builder()
                .name(name.strip()).description(MemberService.emptyToNull(description)).type(type)
                .team(organizationService.getTeam(teamId))
                .sortOrder((int) boardRepository.count()).created(LocalDateTime.now()).build());
    }

    public void deleteBoard(Long id) {
        if (postRepository.countByBoardId(id) > 0) throw new BusinessException("글이 있는 게시판은 지울 수 없어요.");
        boardRepository.deleteById(id);
    }

    // ---------- 글 ----------

    @Transactional(readOnly = true)
    public List<Post> getPosts(Board board, IssueStatus status) {
        return status == null ? postRepository.findByBoardIdOrderByPinnedDescIdDesc(board.getId())
                : postRepository.findByBoardIdAndIssueStatusOrderByPinnedDescIdDesc(board.getId(), status);
    }

    // 이슈 게시판 목록 : 상태 · 지점 · 분류 · 기간으로 거르기 (고정 글 먼저, 최신순)
    @Transactional(readOnly = true)
    public List<Post> getIssuePosts(Board board, IssueFilter filter) {
        return postRepository.findByBoardIdOrderByPinnedDescIdDesc(board.getId()).stream().filter(filter::matches).toList();
    }

    // 이슈 통계 (지점 필터 반영) : 처리 대기 중 = 해결 완료가 아닌 모든 단계 (ISS-05)
    @Transactional(readOnly = true)
    public IssueSummary issueSummary(Board board, Long workplaceId) {
        List<Post> all = getIssuePosts(board, new IssueFilter(null, workplaceId, null, null, null));
        List<Post> open = all.stream().filter(p -> p.getIssueStatus() != IssueStatus.RESOLVED).toList();
        List<Post> done = all.stream().filter(p -> p.getIssueStatus() == IssueStatus.RESOLVED).toList();
        long inProgress = open.stream().filter(p -> p.getIssueStatus() == IssueStatus.IN_PROGRESS).count();
        return new IssueSummary(open.size(), inProgress, avg(open), max(open), done.size(), avg(done), max(done));
    }

    private static double avg(List<Post> ps) {
        return ps.isEmpty() ? 0 : Math.round(ps.stream().mapToDouble(Post::getElapsedDays).average().orElse(0) * 10) / 10.0;
    }

    private static double max(List<Post> ps) {
        return ps.stream().mapToDouble(Post::getElapsedDays).max().orElse(0);
    }

    // 이슈 게시판 통계 : 상태별 개수 + 해결률
    @Transactional(readOnly = true)
    public Map<IssueStatus, Long> issueStats(Board board) {
        Map<IssueStatus, Long> stats = new EnumMap<>(IssueStatus.class);
        for (IssueStatus s : IssueStatus.values()) stats.put(s, postRepository.countByBoardIdAndIssueStatus(board.getId(), s));
        return stats;
    }

    @Transactional(readOnly = true)
    public List<Post> getRecent(Member me, int size) {
        return postRepository.findTop20ByOrderByIdDesc().stream()
                .filter(p -> p.getBoard().canAccess(me)).limit(size).toList();
    }

    @Transactional(readOnly = true)
    public List<Post> getPinned(Member me) {
        return postRepository.findTop5ByPinnedTrueOrderByIdDesc().stream().filter(p -> p.getBoard().canAccess(me)).toList();
    }

    public Post read(Long postId, Member me) {
        Post p = getPost(postId, me);
        p.setViewCount(p.getViewCount() + 1);
        return p;
    }

    @Transactional(readOnly = true)
    public Post getPost(Long postId, Member me) {
        Post p = postRepository.findById(postId).orElseThrow(() -> new EntityNotFoundException("글을 찾을 수 없어요."));
        if (!p.getBoard().canAccess(me)) throw new NoAccessException("이 글을 볼 권한이 없어요. 팀 게시판은 그 팀 직원만 볼 수 있어요.");
        return p;
    }

    public boolean canEdit(Post p, Member me) {
        return me.isAdmin() || (p.getAuthor() != null && p.getAuthor().getId().equals(me.getId()));
    }

    // [신뢰성] 중복 등록 방지 : 같은 submitToken 으로 이미 쓴 글이 있으면 그 글을 돌려준다
    public Post write(Long boardId, Member me, PostForm form, List<MultipartFile> files) {
        Optional<Post> dup = postRepository.findBySubmitToken(form.getSubmitToken());
        if (dup.isPresent()) return dup.get();

        Board board = getBoard(boardId, me);
        Post post = Post.builder()
                .board(board)
                .author(me)
                .title(form.getTitle().strip())
                .content(form.getContent())
                .pinned(me.isAdmin() && form.isPinned())
                .issueStatus(board.isIssueBoard() ? IssueStatus.REPORTED : null)
                .category(board.isIssueBoard() ? (form.getCategory() == null ? IssueCategory.ETC : form.getCategory()) : null)
                .workplace(board.isIssueBoard() ? issueWorkplace(form, me) : null)
                .submitToken(form.getSubmitToken())
                .created(LocalDateTime.now())
                .build();
        addFiles(post, files);
        postRepository.save(post);
        if (board.isIssueBoard()) recordHistory(post, null, IssueStatus.REPORTED, me, null);

        // 관리자 고정 공지 → 전 직원(볼 수 있는 사람) 알림
        if (post.isPinned()) {
            List<Member> targets = memberService.getActiveMembers().stream()
                    .filter(board::canAccess).filter(m -> !m.getId().equals(me.getId())).toList();
            outboxService.notify(targets, NotificationType.BOARD, "[공지] " + post.getTitle(),
                    board.getName() + " 에 공지가 올라왔어요.", "/board/post/" + post.getId(), "Post", post.getId(),
                    "post-pinned:" + post.getId());
        }
        return post;
    }

    public Post update(Long postId, Member me, PostForm form, List<MultipartFile> files) {
        Post p = getPost(postId, me);
        if (!canEdit(p, me)) throw new BusinessException("글쓴이와 관리자만 고칠 수 있어요.");
        p.setTitle(form.getTitle().strip());
        p.setContent(form.getContent());
        if (me.isAdmin()) p.setPinned(form.isPinned());
        if (p.getBoard().isIssueBoard()) {
            p.setCategory(form.getCategory() == null ? IssueCategory.ETC : form.getCategory());
            p.setWorkplace(issueWorkplace(form, me));
        }
        p.setUpdated(LocalDateTime.now());
        addFiles(p, files);
        return p;
    }

    public Long delete(Long postId, Member me) {
        Post p = getPost(postId, me);
        if (!canEdit(p, me)) throw new BusinessException("글쓴이와 관리자만 지울 수 있어요.");
        Long boardId = p.getBoard().getId();
        // 첨부 파일은 글이 실제로 지워진(커밋된) 뒤에 정리한다
        p.getFiles().forEach(f -> fileStore.deleteAfterCommit(f.getPath()));
        p.getResolutionFiles().forEach(f -> fileStore.deleteAfterCommit(f.getPath()));
        postCommentRepository.deleteByPostId(postId);
        historyRepository.deleteByPostId(postId);
        postRepository.delete(p);
        return boardId;
    }

    public void togglePin(Long postId, Member me) {
        if (!me.isAdmin()) throw new BusinessException("고정은 관리자만 할 수 있어요.");
        Post p = getPost(postId, me);
        p.setPinned(!p.isPinned());
    }

    // 이슈 지점 : 고르지 않으면 글쓴이의 근무지
    private com.mysite.sbb.organization.entity.Workplace issueWorkplace(PostForm form, Member me) {
        var w = organizationService.getWorkplace(form.getWorkplaceId());
        return w != null ? w : me.getWorkplace();
    }

    // ---------- 이슈 상태 흐름 : 보고 완료 → 검토 중 → (처리 대기 중) → 처리 중 → 해결 완료 ----------
    // [신뢰성] REL-40 : 상태 + 이력 + 알림 이벤트를 이 트랜잭션 하나로 저장한다 (중간에 실패하면 모두 롤백)

    @Transactional(readOnly = true)
    public List<IssueStatusHistory> getHistory(Long postId) {
        return historyRepository.findTimeline(postId);
    }

    // 상태 칩 메뉴 (관리자) : 상태도에 있는 다음 단계로만 바꾼다
    @Retryable(includes = {ConcurrencyFailureException.class, TransientDataAccessException.class,
            CannotCreateTransactionException.class}, maxRetries = 3, delay = 100, multiplier = 2)
    public void changeStatus(Long postId, Member me, IssueStatus to, String memo, String resolution) {
        if (!me.isAdmin()) throw new NoAccessException("이슈 단계는 관리자만 바꿀 수 있어요.");
        Post p = lockedIssue(postId, me);
        IssueStatus from = p.getIssueStatus();
        if (from == to) return;     // 같은 단계를 다시 눌렀거나 두 번 제출 : 아무 일도 없다
        if (from == null || !from.canMoveTo(to)) {
            throw new BusinessException("'" + (from == null ? "-" : from.getLabel()) + "' 에서 '" + to.getLabel() + "' 로는 바꿀 수 없어요.");
        }
        moveTo(p, to, me, memo, resolution, null);
    }

    // 처리 시작 (기존 버튼) : 해결 전 어느 단계에서든 처리 중으로
    public void startIssue(Long postId, Member me) {
        Post p = lockedIssue(postId, me);
        IssueStatus from = p.getIssueStatus();
        if (from == IssueStatus.RESOLVED || from == IssueStatus.IN_PROGRESS) {
            throw new BusinessException("처리 대기 중인 이슈만 시작할 수 있어요.");
        }
        moveTo(p, IssueStatus.IN_PROGRESS, me, null, null, null);
    }

    // 해결 등록 (기존 폼) : 해결 내용(필수) + 첨부, 해결자·시각 기록, 글쓴이에게 알림
    public void resolveIssue(Long postId, Member me, String resolution, List<MultipartFile> files) {
        Post p = lockedIssue(postId, me);
        if (p.getIssueStatus() == IssueStatus.RESOLVED) throw new BusinessException("이미 해결된 이슈예요.");
        moveTo(p, IssueStatus.RESOLVED, me, null, resolution, files);
    }

    // 해결 취소 : 해결한 사람 · 글쓴이 · 매니저 · 관리자. 해결 내용과 첨부를 비우고 검토 중으로 되돌린다
    public void reopenIssue(Long postId, Member me) {
        Post p = lockedIssue(postId, me);
        if (p.getIssueStatus() != IssueStatus.RESOLVED) throw new BusinessException("해결된 이슈만 취소할 수 있어요.");
        if (!canReopen(p, me)) throw new BusinessException("해결한 사람, 글쓴이, 매니저, 관리자만 해결을 취소할 수 있어요.");
        moveTo(p, IssueStatus.REVIEWING, me, "해결 취소", null, null);
    }

    public boolean canReopen(Post p, Member me) {
        return p.getIssueStatus() == IssueStatus.RESOLVED && (me.isAdmin() || me.isManager()
                || (p.getResolvedBy() != null && p.getResolvedBy().getId().equals(me.getId()))
                || (p.getAuthor() != null && p.getAuthor().getId().equals(me.getId())));
    }

    // 예전 상태 폼 호환 : 어느 단계로든 바꿀 수 있고, 해결로 바꿀 때는 해결 방법이 꼭 있어야 한다
    public void changeIssue(Long postId, Member me, IssueStatus status, String resolution) {
        Post p = lockedIssue(postId, me);
        if (p.getIssueStatus() == status) {
            if (status == IssueStatus.RESOLVED && resolution != null && !resolution.isBlank()) p.setResolution(resolution.strip());
            return;
        }
        moveTo(p, status, me, null, resolution, null);
    }

    // 모든 상태 변경이 지나는 한 곳 : 해결 값 정리 → 상태 → 이력 → 글쓴이 알림
    private void moveTo(Post p, IssueStatus to, Member me, String memo, String resolution, List<MultipartFile> files) {
        IssueStatus from = p.getIssueStatus();
        if (to == IssueStatus.RESOLVED) {
            if (resolution == null || resolution.isBlank()) throw new BusinessException("해결 내용을 적어 주세요.");
            p.setResolution(resolution.strip());
            p.setResolvedAt(LocalDateTime.now());
            p.setResolvedBy(me);
            clearResolutionFiles(p);
            if (files != null) {
                for (MultipartFile f : files) {
                    FileRef ref = fileStore.storeAttachment(f);
                    if (ref != null) p.getResolutionFiles().add(ref);
                }
            }
        } else if (from == IssueStatus.RESOLVED) {
            p.setResolution(null);
            p.setResolvedAt(null);
            p.setResolvedBy(null);
            clearResolutionFiles(p);
        }
        p.setIssueStatus(to);
        String note = MemberService.emptyToNull(memo);
        recordHistory(p, from, to, me, note);
        if (p.getAuthor() != null && !p.getAuthor().getId().equals(me.getId())) {
            outboxService.notify(List.of(p.getAuthor()), NotificationType.BOARD, "이슈 " + to.getLabel() + " : " + p.getTitle(),
                    me.getDisplayName() + " 님이 상태를 '" + to.getLabel() + "' 로 바꿨어요." + (note == null ? "" : " (" + note + ")"),
                    "/board/post/" + p.getId(), "Post", p.getId(), null);
        }
    }

    private void recordHistory(Post p, IssueStatus from, IssueStatus to, Member me, String memo) {
        historyRepository.save(IssueStatusHistory.builder().post(p).fromStatus(from).toStatus(to)
                .changedBy(me).memo(memo).changedAt(LocalDateTime.now()).build());
    }

    private void clearResolutionFiles(Post p) {
        p.getResolutionFiles().forEach(f -> fileStore.deleteAfterCommit(f.getPath()));   // 커밋 뒤 정리
        p.getResolutionFiles().clear();
    }

    private Post lockedIssue(Long postId, Member me) {
        Post p = postRepository.findByIdForUpdate(postId).orElseThrow(() -> new EntityNotFoundException("글을 찾을 수 없어요."));
        if (!p.getBoard().canAccess(me)) throw new NoAccessException("이 글을 볼 권한이 없어요. 팀 게시판은 그 팀 직원만 볼 수 있어요.");
        if (!p.getBoard().isIssueBoard()) throw new BusinessException("이슈 게시판 글이 아니에요.");
        return p;
    }

    // ---------- 첨부 접근 권한 (SEC-05) ----------

    // 글의 첨부 하나 (저장 이름으로 찾는다). 볼 권한이 없으면 예외
    @Transactional(readOnly = true)
    public FileRef getPostFile(Long postId, String storedName, Member me) {
        Post p = getPost(postId, me);
        String path = "/uploads/" + storedName;
        return java.util.stream.Stream.concat(p.getFiles().stream(), p.getResolutionFiles().stream())
                .filter(f -> path.equals(f.getPath())).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("첨부를 찾을 수 없어요."));
    }

    // /uploads/** 직접 주소 : 팀 전용 게시판 글의 첨부면 그 게시판 권한이 있어야 한다
    @Transactional(readOnly = true)
    public boolean canReadUpload(String webPath, Member me) {
        return java.util.stream.Stream.concat(postRepository.findByFilePath(webPath).stream(),
                        postRepository.findByResolutionFilePath(webPath).stream())
                .allMatch(p -> p.getBoard().canAccess(me));
    }

    // ---------- 댓글 ----------

    @Transactional(readOnly = true)
    public List<PostComment> getComments(Long postId) {
        return postCommentRepository.findByPostIdOrderByIdAsc(postId);
    }

    public void addComment(Long postId, Member me, String content) {
        if (content == null || content.isBlank()) return;
        Post p = getPost(postId, me);
        postCommentRepository.save(PostComment.builder().post(p).author(me).content(content.strip()).created(LocalDateTime.now()).build());

        Set<Member> targets = new LinkedHashSet<>(memberService.findMentioned(content));
        if (p.getAuthor() != null) targets.add(p.getAuthor());
        targets.removeIf(m -> m.getId().equals(me.getId()));
        outboxService.notify(targets, NotificationType.BOARD, "새 댓글 : " + p.getTitle(),
                me.getDisplayName() + " : " + content.strip(), "/board/post/" + p.getId(), "Post", p.getId(), null);
    }

    private void addFiles(Post post, List<MultipartFile> files) {
        if (files == null) return;
        for (MultipartFile f : files) {
            FileRef ref = fileStore.storeAttachment(f);     // 롤백되면 FileStore 가 지운다 (보상)
            if (ref != null) post.getFiles().add(ref);
        }
    }
}
