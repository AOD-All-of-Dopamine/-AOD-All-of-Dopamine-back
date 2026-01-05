package com.example.AOD.api.service;

import com.example.AOD.api.dto.PageResponse;
import com.example.AOD.api.dto.WorkSummaryDTO;
import com.example.AOD.domain.Bookmark;
import com.example.shared.entity.Content;
import com.example.AOD.repo.BookmarkRepository;
import com.example.shared.repository.ContentRepository;
// import com.example.AOD.recommendation.repository.ContentRatingRepository;
import com.example.AOD.user.model.User;
import com.example.AOD.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final ContentRepository contentRepository;
    private final UserRepository userRepository;
    // private final ContentRatingRepository contentRatingRepository;

    /**
     * 북마크 토글
     */
    @Transactional
    public Map<String, Object> toggleBookmark(Long contentId, String username) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new RuntimeException("Content not found: " + contentId));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        Optional<Bookmark> existing = bookmarkRepository.findByContentAndUser(content, user);

        if (existing.isPresent()) {
            // 이미 북마크 되어있으면 삭제
            bookmarkRepository.delete(existing.get());
            return Map.of(
                    "contentId", contentId,
                    "bookmarked", false,
                    "message", "북마크가 해제되었습니다."
            );
        } else {
            // 북마크 추가
            Bookmark bookmark = new Bookmark();
            bookmark.setContent(content);
            bookmark.setUser(user);
            bookmarkRepository.save(bookmark);
            return Map.of(
                    "contentId", contentId,
                    "bookmarked", true,
                    "message", "북마크에 추가되었습니다."
            );
        }
    }

    /**
     * 내 북마크 목록 조회
     */
    public PageResponse<WorkSummaryDTO> getMyBookmarks(String username, Pageable pageable) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        Page<Bookmark> bookmarkPage = bookmarkRepository.findByUser(user, pageable);

        List<WorkSummaryDTO> content = bookmarkPage.getContent().stream()
                .map(bookmark -> toWorkSummary(bookmark.getContent()))
                .collect(Collectors.toList());

        return PageResponse.<WorkSummaryDTO>builder()
                .content(content)
                .page(bookmarkPage.getNumber())
                .size(bookmarkPage.getSize())
                .totalElements(bookmarkPage.getTotalElements())
                .totalPages(bookmarkPage.getTotalPages())
                .first(bookmarkPage.isFirst())
                .last(bookmarkPage.isLast())
                .build();
    }

    /**
     * 북마크 여부 확인
     */
    public Map<String, Object> getBookmarkStatus(Long contentId, String username) {
        if (username == null) {
            return Map.of("contentId", contentId, "bookmarked", false);
        }

        Content content = contentRepository.findById(contentId).orElse(null);
        User user = userRepository.findByUsername(username).orElse(null);

        boolean bookmarked = false;
        if (content != null && user != null) {
            bookmarked = bookmarkRepository.existsByContentAndUser(content, user);
        }

        return Map.of("contentId", contentId, "bookmarked", bookmarked);
    }

    private WorkSummaryDTO toWorkSummary(Content content) {
        return WorkSummaryDTO.builder()
                .id(content.getContentId())
                .domain(content.getDomain().name())
                .title(content.getMasterTitle())
                .thumbnail(content.getPosterImageUrl())
                .releaseDate(content.getReleaseDate() != null ? content.getReleaseDate().toString() : null)
                .score(calculateAverageScore(content.getContentId()))
                .build();
    }

    private Double calculateAverageScore(Long contentId) {
        // TODO: 추천 기능 추가 후 활성화
        // Double avg = contentRatingRepository.getAverageRatingByContentTypeAndId("GAME", contentId);
        // if (avg == null) avg = contentRatingRepository.getAverageRatingByContentTypeAndId("AV", contentId);
        // if (avg == null) avg = contentRatingRepository.getAverageRatingByContentTypeAndId("WEBTOON", contentId);
        // if (avg == null) avg = contentRatingRepository.getAverageRatingByContentTypeAndId("WEBNOVEL", contentId);
        // return avg != null ? avg : 0.0;
        return 0.0;
    }
}


