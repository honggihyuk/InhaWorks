package com.mysite.sbb.board.service;

import com.mysite.sbb.board.constant.IssueCategory;
import com.mysite.sbb.board.dto.IssueSummary;
import com.mysite.sbb.board.constant.BoardType;
import com.mysite.sbb.board.constant.IssueStatus;
import com.mysite.sbb.board.dto.PostForm;
import com.mysite.sbb.board.entity.Board;
import com.mysite.sbb.board.entity.Post;
import com.mysite.sbb.board.entity.PostComment;
import com.mysite.sbb.board.repository.BoardRepository;
import com.mysite.sbb.board.repository.PostCommentRepository;
import com.mysite.sbb.board.repository.PostRepository;
import com.mysite.sbb.common.entity.FileRef;
import com.mysite.sbb.common.exception.BusinessException;
import com.mysite.sbb.common.file.FileStore;
import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.member.service.MemberService;
import com.mysite.sbb.notification.constant.NotificationType;
import com.mysite.sbb.organization.service.OrganizationService;
import com.mysite.sbb.outbox.service.OutboxService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
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
        if (!b.canAccess(me)) throw new BusinessException("팀 게시판은 그 팀 직원만 볼 수 있어요.");
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

    // 이슈 게시판 목록 : 상태 · 지점 · 분류로 거르기 (고정 글 먼저, 최신순)
    @Transactional(readOnly = true)
    public List<Post> getIssuePosts(Board board, IssueStatus status, Long workplaceId, IssueCategory category) {
        return postRepository.findByBoardIdOrderByPinnedDescIdDesc(board.getId()).stream()
                .filter(p -> status == null || p.getIssueStatus() == status
                        || (status == IssueStatus.OPEN && p.getIssueStatus() == IssueStatus.IN_PROGRESS))
                .filter(p -> workplaceId == null || (p.getWorkplace() != null && p.getWorkplace().getId().equals(workplaceId)))
                .filter(p -> category == null || p.getCategory() == category)
                .toList();
    }

    // 이슈 통계 (지점 필터 반영) : 처리 대기(처리 중 포함) 건수·미해결 기간, 해결 완료 건수·해결 소요 기간
    @Transactional(readOnly = true)
    public IssueSummary issueSummary(Board board, Long workplaceId) {
        List<Post> all = getIssuePosts(board, null, workplaceId, null);
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
        if (!p.getBoard().canAccess(me)) throw new BusinessException("팀 게시판은 그 팀 직원만 볼 수 있어요.");
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
                .issueStatus(board.isIssueBoard() ? IssueStatus.OPEN : null)
                .category(board.isIssueBoard() ? (form.getCategory() == null ? IssueCategory.ETC : form.getCategory()) : null)
                .workplace(board.isIssueBoard() ? issueWorkplace(form, me) : null)
                .submitToken(form.getSubmitToken())
                .created(LocalDateTime.now())
                .build();
        addFiles(post, files);
        postRepository.save(post);

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
        postCommentRepository.deleteByPostId(postId);
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

    // ---------- 이슈 해결 흐름 : 처리 대기 → (처리 시작) → 해결 등록 → (해결 취소) ----------

    // 처리 시작 : 누가 맡았는지 알 수 있게 상태만 바꾼다
    public void startIssue(Long postId, Member me) {
        Post p = issue(postId, me);
        if (p.getIssueStatus() != IssueStatus.OPEN) throw new BusinessException("처리 대기 중인 이슈만 시작할 수 있어요.");
        p.setIssueStatus(IssueStatus.IN_PROGRESS);
        notifyAuthor(p, me, "처리를 시작했어요.");
    }

    // 해결 등록 : 해결 내용(필수) + 첨부, 해결자·시각 기록, 글쓴이에게 알림
    public void resolveIssue(Long postId, Member me, String resolution, List<MultipartFile> files) {
        Post p = issue(postId, me);
        if (p.getIssueStatus() == IssueStatus.RESOLVED) throw new BusinessException("이미 해결된 이슈예요.");
        if (resolution == null || resolution.isBlank()) throw new BusinessException("해결 내용을 적어 주세요.");
        p.setIssueStatus(IssueStatus.RESOLVED);
        p.setResolution(resolution.strip());
        p.setResolvedAt(LocalDateTime.now());
        p.setResolvedBy(me);
        p.getResolutionFiles().clear();
        if (files != null) {
            for (MultipartFile f : files) {
                FileRef ref = fileStore.storeAttachment(f);
                if (ref != null) p.getResolutionFiles().add(ref);
            }
        }
        notifyAuthor(p, me, "해결을 등록했어요.");
    }

    // 해결 취소 : 해결한 사람 · 글쓴이 · 매니저 · 관리자. 해결 내용과 첨부를 비우고 처리 대기로 되돌린다
    public void reopenIssue(Long postId, Member me) {
        Post p = issue(postId, me);
        if (p.getIssueStatus() != IssueStatus.RESOLVED) throw new BusinessException("해결된 이슈만 취소할 수 있어요.");
        boolean allowed = me.isAdmin() || me.isManager()
                || (p.getResolvedBy() != null && p.getResolvedBy().getId().equals(me.getId()))
                || (p.getAuthor() != null && p.getAuthor().getId().equals(me.getId()));
        if (!allowed) throw new BusinessException("해결한 사람, 글쓴이, 매니저, 관리자만 해결을 취소할 수 있어요.");
        p.setIssueStatus(IssueStatus.OPEN);
        p.setResolution(null);
        p.setResolvedAt(null);
        p.setResolvedBy(null);
        p.getResolutionFiles().clear();
        notifyAuthor(p, me, "해결을 취소했어요. 다시 처리 대기예요.");
    }

    public boolean canReopen(Post p, Member me) {
        return p.getIssueStatus() == IssueStatus.RESOLVED && (me.isAdmin() || me.isManager()
                || (p.getResolvedBy() != null && p.getResolvedBy().getId().equals(me.getId()))
                || (p.getAuthor() != null && p.getAuthor().getId().equals(me.getId())));
    }

    private Post issue(Long postId, Member me) {
        Post p = getPost(postId, me);
        if (!p.getBoard().isIssueBoard()) throw new BusinessException("이슈 게시판 글이 아니에요.");
        return p;
    }

    private void notifyAuthor(Post p, Member me, String what) {
        if (p.getAuthor() != null && !p.getAuthor().getId().equals(me.getId())) {
            outboxService.notify(List.of(p.getAuthor()), NotificationType.BOARD, "이슈 " + p.getIssueStatus().getLabel() + " : " + p.getTitle(),
                    me.getDisplayName() + " 님이 " + what, "/board/post/" + p.getId(), "Post", p.getId(), null);
        }
    }

    // 이슈 상태 바꾸기 (예전 폼 호환) : 해결로 바꿀 때는 해결 방법이 꼭 있어야 한다
    public void changeIssue(Long postId, Member me, IssueStatus status, String resolution) {
        Post p = getPost(postId, me);
        if (!p.getBoard().isIssueBoard()) throw new BusinessException("이슈 게시판 글이 아니에요.");
        if (status == IssueStatus.RESOLVED && (resolution == null || resolution.isBlank())) {
            throw new BusinessException("해결 방법을 적어 주세요.");
        }
        p.setIssueStatus(status);
        if (resolution != null && !resolution.isBlank()) p.setResolution(resolution.strip());
        p.setResolvedAt(status == IssueStatus.RESOLVED ? LocalDateTime.now() : null);
        p.setResolvedBy(status == IssueStatus.RESOLVED ? me : null);
        if (p.getAuthor() != null && !p.getAuthor().getId().equals(me.getId())) {
            outboxService.notify(List.of(p.getAuthor()), NotificationType.BOARD, "이슈 " + status.getLabel() + " : " + p.getTitle(),
                    me.getDisplayName() + " 님이 상태를 '" + status.getLabel() + "' 로 바꿨어요.", "/board/post/" + p.getId(),
                    "Post", p.getId(), null);
        }
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
