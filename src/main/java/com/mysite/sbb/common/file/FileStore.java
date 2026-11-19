package com.mysite.sbb.common.file;

import com.mysite.sbb.common.entity.FileRef;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

// 업로드한 파일을 서버 폴더(app.upload-dir)에 저장하고, 화면에서 쓸 주소(/uploads/파일명)를 돌려준다.
//
// [신뢰성] 보상 처리 (Compensation)
//  파일은 DB 트랜잭션 밖(디스크)에 저장되기 때문에, 파일을 저장한 뒤 DB 저장이 실패(롤백)하면
//  "DB 에는 없는데 디스크에만 남는 고아 파일"이 생긴다.
//  → 트랜잭션 안에서 저장하면 TransactionSynchronization 을 등록해 두고,
//    트랜잭션이 커밋되지 않고 끝나면(afterCompletion : ROLLED_BACK) 방금 저장한 파일을 지운다.
@Slf4j
@Component
public class FileStore {

    private static final Set<String> IMAGES = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final Set<String> DOCUMENTS = Set.of("jpg", "jpeg", "png", "gif", "webp",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "hwp", "hwpx", "txt", "csv", "zip");
    private static final String PNG_PREFIX = "data:image/png;base64,";

    private final Path uploadDir;

    public FileStore(@Value("${app.upload-dir:uploads}") String uploadDir) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    public Path getUploadDir() {
        return uploadDir;
    }

    // 프로필 사진 (이미지만). 파일이 비어 있으면 null
    public String store(MultipartFile file) {
        FileRef ref = storeImage(file);
        return ref == null ? null : ref.getPath();
    }

    // 사진 항목 (할 일 사진, 보고서 이미지)
    public FileRef storeImage(MultipartFile file) {
        return save(file, IMAGES, "이미지 파일(jpg, png, gif, webp)만 올릴 수 있어요.");
    }

    // 첨부 문서 (가이드 첨부, 게시글 첨부, 보고서 문서)
    public FileRef storeAttachment(MultipartFile file) {
        return save(file, DOCUMENTS, "올릴 수 없는 파일 형식이에요. (이미지, pdf, 오피스, hwp, txt, zip)");
    }

    // 서명 : 화면의 canvas 가 보낸 "data:image/png;base64,...." 를 PNG 파일로 저장. 비어 있으면 null
    public FileRef storeSignature(String dataUrl) {
        if (dataUrl == null || !dataUrl.startsWith(PNG_PREFIX)) {
            return null;
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(dataUrl.substring(PNG_PREFIX.length()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("서명 데이터가 올바르지 않아요.");
        }
        String saveName = UUID.randomUUID() + ".png";
        Path target = uploadDir.resolve(saveName);
        try {
            Files.createDirectories(uploadDir);
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new IllegalStateException("서명 저장에 실패했습니다.", e);
        }
        deleteOnRollback(target);
        return new FileRef("signature.png", "/uploads/" + saveName, (long) bytes.length);
    }

    // 화면 주소(/uploads/xxx) → 실제 파일 위치 (zip 다운로드에서 사용)
    public Path resolve(String webPath) {
        if (webPath == null || !webPath.startsWith("/uploads/")) {
            return null;
        }
        Path p = uploadDir.resolve(webPath.substring("/uploads/".length())).normalize();
        return p.startsWith(uploadDir) ? p : null;     // ../ 로 폴더 밖을 가리키지 못하게
    }

    private FileRef save(MultipartFile file, Set<String> allowed, String errorMessage) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        original = original.substring(Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\')) + 1);
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!allowed.contains(ext)) {
            throw new IllegalArgumentException(errorMessage);
        }
        String saveName = UUID.randomUUID() + "." + ext;   // 파일 이름이 겹치지 않도록 UUID 사용
        Path target = uploadDir.resolve(saveName);
        // transferTo 는 임시 파일을 옮겨 버려서 (재시도할 때) 다시 읽을 수 없으므로 복사한다
        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(uploadDir);
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("파일 저장에 실패했습니다.", e);
        }
        deleteOnRollback(target);
        return new FileRef(original, "/uploads/" + saveName, file.getSize());
    }

    // 트랜잭션이 롤백되면 저장한 파일을 지운다 (보상 처리)
    private void deleteOnRollback(Path target) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;   // 트랜잭션 밖에서 저장한 경우 (예: 프로필 사진) 는 그대로
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try {
                        Files.deleteIfExists(target);
                        log.warn("=======> 트랜잭션 롤백 → 업로드 파일 삭제(보상) : {}", target.getFileName());
                    } catch (IOException e) {
                        log.error("=======> 보상 삭제 실패 : {}", target, e);
                    }
                }
            }
        });
    }
}
