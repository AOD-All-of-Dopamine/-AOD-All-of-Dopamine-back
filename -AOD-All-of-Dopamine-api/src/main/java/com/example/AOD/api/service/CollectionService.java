package com.example.AOD.api.service;

import com.example.AOD.api.dto.PageResponse;
import com.example.AOD.api.dto.WorkSummaryDTO;
import com.example.AOD.api.dto.collection.*;
import com.example.AOD.domain.Collection;
import com.example.AOD.domain.CollectionItem;
import com.example.AOD.repo.CollectionItemRepository;
import com.example.AOD.repo.CollectionLikeRepository;
import com.example.AOD.repo.CollectionRepository;
import com.example.AOD.user.model.User;
import com.example.AOD.user.repository.UserRepository;
import com.example.shared.entity.Content;
import com.example.shared.entity.Domain;
import com.example.shared.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 컬렉션 기능 (2026-08 플랜) — 목록/상세/CRUD/아이템/좋아요.
 * - PRIVATE 컬렉션: 소유자 외 403 / 수정·삭제·아이템 조작: 소유자만
 * - like_count·view_count는 리포지토리의 원자적 UPDATE로만 갱신 (경합 안전)
 * - 상세 items의 도메인별 표시 필드는 WorkApiService 보강 로직 재사용
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CollectionService {

    private final CollectionRepository collectionRepository;
    private final CollectionItemRepository collectionItemRepository;
    private final CollectionLikeRepository collectionLikeRepository;
    private final ContentRepository contentRepository;
    private final UserRepository userRepository;
    private final WorkApiService workApiService;

    // ========== 목록 ==========

    /** 발견 페이지 — 공개 컬렉션 목록 (sort: popular=like_count desc / latest=created_at desc) */
    public PageResponse<CollectionSummaryDTO> getPublicCollections(String sort, Domain domain,
                                                                   String username, int page, int size) {
        Sort sortSpec = parseSort(sort);
        Pageable pageable = PageRequest.of(page, size, sortSpec);

        Page<Collection> collectionPage = (domain != null)
                ? collectionRepository.findByVisibilityAndDomain(Collection.Visibility.PUBLIC, domain, pageable)
                : collectionRepository.findByVisibility(Collection.Visibility.PUBLIC, pageable);

        return buildSummaryPage(collectionPage, findUserOrNull(username));
    }

    /** 내 컬렉션 목록 (비공개 포함) */
    public PageResponse<CollectionSummaryDTO> getMyCollections(String username, int page, int size) {
        User user = requireUser(username);
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<Collection> collectionPage = collectionRepository.findByUser(user, pageable);
        return buildSummaryPage(collectionPage, user);
    }

    /** 담기 팝오버용 — 해당 작품 도메인의 내 컬렉션 + 각각에 작품 포함 여부 */
    public List<MyCollectionSummaryDTO> getMyCollectionSummaries(String username, Long contentId) {
        User user = requireUser(username);
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "작품을 찾을 수 없습니다: " + contentId));

        List<Collection> collections =
                collectionRepository.findByUserAndDomainOrderByCreatedAtDesc(user, content.getDomain());
        if (collections.isEmpty()) return List.of();

        List<Long> ids = collections.stream().map(Collection::getId).collect(Collectors.toList());
        Map<Long, Long> itemCounts = toCountMap(collectionItemRepository.countByCollectionIds(ids));
        Set<Long> containing = new HashSet<>(
                collectionItemRepository.findCollectionIdsContainingContent(ids, contentId));

        return collections.stream()
                .map(c -> MyCollectionSummaryDTO.builder()
                        .id(c.getId())
                        .title(c.getTitle())
                        .domain(c.getDomain().name())
                        .tint(c.getTint().name())
                        .visibility(c.getVisibility().name())
                        .itemCount(itemCounts.getOrDefault(c.getId(), 0L))
                        .containsContent(containing.contains(c.getId()))
                        .build())
                .collect(Collectors.toList());
    }

    // ========== 상세 ==========

    /** 상세 조회 + 조회수 +1 (원자적 UPDATE). PRIVATE는 소유자 외 403. */
    @Transactional
    public CollectionDetailDTO getCollectionDetail(Long collectionId, String username) {
        Collection collection = requireCollection(collectionId);
        User viewer = findUserOrNull(username);
        boolean owner = isOwner(collection, viewer);
        requireVisible(collection, owner);

        collectionRepository.incrementViewCount(collectionId);

        List<CollectionItem> items =
                collectionItemRepository.findByCollectionIdOrderByPositionAscIdAsc(collectionId);
        List<Content> contents = items.stream().map(CollectionItem::getContent).collect(Collectors.toList());
        // 도메인별 표시 필드 보강 — WorkApiService의 목록 카드 보강 로직 재사용 (입력 순서 유지)
        List<WorkSummaryDTO> enriched = workApiService.toEnrichedSummaries(contents);

        List<CollectionItemDTO> itemDtos = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            CollectionItem item = items.get(i);
            itemDtos.add(CollectionItemDTO.of(item.getId(), item.getComment(), item.getPosition(), enriched.get(i)));
        }

        boolean likedByMe = viewer != null
                && collectionLikeRepository.existsByCollectionIdAndUserId(collectionId, viewer.getId());
        List<String> coverPosters = itemDtos.stream()
                .limit(3)
                .map(CollectionItemDTO::getPosterUrl)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return CollectionDetailDTO.builder()
                .id(collection.getId())
                .title(collection.getTitle())
                .description(collection.getDescription())
                .domain(collection.getDomain().name())
                .tint(collection.getTint().name())
                .visibility(collection.getVisibility().name())
                .likeCount(collection.getLikeCount())
                // 벌크 UPDATE는 영속성 컨텍스트를 우회하므로 이번 조회분 +1을 직접 반영
                .viewCount(collection.getViewCount() + 1)
                .itemCount(items.size())
                .curatorNickname(collection.getUser().getUsername())
                .coverPosters(coverPosters)
                .likedByMe(likedByMe)
                .owner(owner)
                .createdAt(collection.getCreatedAt())
                .updatedAt(collection.getUpdatedAt())
                .items(itemDtos)
                .build();
    }

    // ========== 컬렉션 CRUD ==========

    @Transactional
    public CollectionSummaryDTO createCollection(String username, CollectionCreateRequest request) {
        User user = requireUser(username);

        String title = validateTitle(request.getTitle());
        String description = validateDescription(request.getDescription());
        Domain domain = parseEnum(Domain.class, request.getDomain(), true, "domain");
        Collection.Tint tint = parseEnum(Collection.Tint.class, request.getTint(), false, "tint");
        Collection.Visibility visibility =
                parseEnum(Collection.Visibility.class, request.getVisibility(), false, "visibility");

        Collection collection = new Collection();
        collection.setUser(user);
        collection.setTitle(title);
        collection.setDescription(description);
        collection.setDomain(domain);
        if (tint != null) collection.setTint(tint);
        if (visibility != null) collection.setVisibility(visibility);

        Collection saved = collectionRepository.save(collection);
        return toSummary(saved, 0L, List.of(), false);
    }

    @Transactional
    public CollectionSummaryDTO updateCollection(Long collectionId, String username,
                                                 CollectionUpdateRequest request) {
        Collection collection = requireOwnedCollection(collectionId, username);

        if (request.getTitle() != null) collection.setTitle(validateTitle(request.getTitle()));
        if (request.getDescription() != null) {
            String description = validateDescription(request.getDescription());
            collection.setDescription(description == null || description.isBlank() ? null : description);
        }
        Collection.Tint tint = parseEnum(Collection.Tint.class, request.getTint(), false, "tint");
        if (tint != null) collection.setTint(tint);
        Collection.Visibility visibility =
                parseEnum(Collection.Visibility.class, request.getVisibility(), false, "visibility");
        if (visibility != null) collection.setVisibility(visibility);

        Collection saved = collectionRepository.save(collection);
        long itemCount = collectionItemRepository.countByCollectionId(collectionId);
        List<String> covers = coverPostersOf(collectionId);
        boolean likedByMe = collectionLikeRepository.existsByCollectionIdAndUserId(
                collectionId, collection.getUser().getId());
        return toSummary(saved, itemCount, covers, likedByMe);
    }

    @Transactional
    public void deleteCollection(Long collectionId, String username) {
        Collection collection = requireOwnedCollection(collectionId, username);
        // DDL의 ON DELETE CASCADE와 무관하게 명시 삭제로 정합성 보장
        collectionItemRepository.deleteAllByCollectionId(collectionId);
        collectionLikeRepository.deleteAllByCollectionId(collectionId);
        collectionRepository.delete(collection);
    }

    // ========== 아이템 ==========

    /** 아이템 추가 — 도메인 불일치 400, 중복 409, position은 말미 */
    @Transactional
    public CollectionItemDTO addItem(Long collectionId, String username, CollectionItemAddRequest request) {
        Collection collection = requireOwnedCollection(collectionId, username);
        if (request.getContentId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "contentId는 필수입니다.");
        }
        Content content = contentRepository.findById(request.getContentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "작품을 찾을 수 없습니다: " + request.getContentId()));

        if (content.getDomain() != collection.getDomain()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "컬렉션 도메인(" + collection.getDomain() + ")과 작품 도메인(" + content.getDomain() + ")이 일치하지 않습니다.");
        }
        // 성인 콘텐츠는 목록 비노출이지만 상세 직접 URL로 접근 가능 — 담기까지 허용하면
        // 공개 컬렉션을 통해 재노출되므로 능동 우회를 봉쇄 (기존 담긴 아이템은 잔존 허용 — 백로그)
        if (Boolean.TRUE.equals(content.getIsAdult())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "담을 수 없는 작품입니다.");
        }
        if (collectionItemRepository.existsByCollectionIdAndContentContentId(collectionId, content.getContentId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 컬렉션에 담긴 작품입니다.");
        }

        String comment = validateComment(request.getComment());
        Integer maxPosition = collectionItemRepository.findMaxPosition(collectionId);

        CollectionItem item = new CollectionItem();
        item.setCollection(collection);
        item.setContent(content);
        item.setComment(comment == null || comment.isBlank() ? null : comment);
        item.setPosition(maxPosition == null ? 0 : maxPosition + 1);

        CollectionItem saved;
        try {
            // exists 검사 후 save 사이의 동시 요청 경합은 유니크 제약이 막는다 —
            // flush를 강제해 제약 위반을 여기서 잡아 409로 변환 (raw SQL 메시지 노출 방지)
            saved = collectionItemRepository.saveAndFlush(item);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 컬렉션에 담긴 작품입니다.");
        }
        WorkSummaryDTO enriched = workApiService.toEnrichedSummaries(List.of(content)).get(0);
        return CollectionItemDTO.of(saved.getId(), saved.getComment(), saved.getPosition(), enriched);
    }

    @Transactional
    public CollectionItemDTO updateItem(Long collectionId, Long itemId, String username,
                                        CollectionItemUpdateRequest request) {
        requireOwnedCollection(collectionId, username);
        CollectionItem item = requireItemInCollection(collectionId, itemId);

        if (request.getComment() != null) {
            String comment = validateComment(request.getComment());
            item.setComment(comment == null || comment.isBlank() ? null : comment);
        }
        if (request.getPosition() != null) {
            if (request.getPosition() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "position은 0 이상이어야 합니다.");
            }
            item.setPosition(request.getPosition());
        }

        CollectionItem saved = collectionItemRepository.save(item);
        WorkSummaryDTO enriched = workApiService.toEnrichedSummaries(List.of(saved.getContent())).get(0);
        return CollectionItemDTO.of(saved.getId(), saved.getComment(), saved.getPosition(), enriched);
    }

    @Transactional
    public void deleteItem(Long collectionId, Long itemId, String username) {
        requireOwnedCollection(collectionId, username);
        CollectionItem item = requireItemInCollection(collectionId, itemId);
        collectionItemRepository.delete(item);
    }

    /** 일괄 재정렬 — itemIds는 컬렉션의 전체 아이템과 정확히 일치해야 함 */
    @Transactional
    public void reorderItems(Long collectionId, String username, List<Long> itemIds) {
        requireOwnedCollection(collectionId, username);
        if (itemIds == null || itemIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "itemIds는 필수입니다.");
        }

        List<CollectionItem> items =
                collectionItemRepository.findByCollectionIdOrderByPositionAscIdAsc(collectionId);
        Map<Long, CollectionItem> byId = items.stream()
                .collect(Collectors.toMap(CollectionItem::getId, Function.identity()));

        if (itemIds.size() != items.size() || !byId.keySet().equals(new HashSet<>(itemIds))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "itemIds가 컬렉션의 아이템 목록과 일치하지 않습니다.");
        }

        for (int i = 0; i < itemIds.size(); i++) {
            byId.get(itemIds.get(i)).setPosition(i);
        }
        collectionItemRepository.saveAll(items);
    }

    // ========== 좋아요 ==========

    /** 좋아요 — 멱등 (이미 좋아요면 no-op). like_count는 실제 삽입 시에만 원자적 +1. */
    @Transactional
    public Map<String, Object> like(Long collectionId, String username) {
        User user = requireUser(username);
        Collection collection = requireCollection(collectionId);
        requireVisible(collection, isOwner(collection, user));

        int inserted = collectionLikeRepository.insertIfAbsent(collectionId, user.getId());
        if (inserted > 0) {
            collectionRepository.incrementLikeCount(collectionId);
        }
        // collection은 증가 전에 로드된 스냅샷 — 이번 요청의 삽입분만 더해 응답
        return likeResponse(collectionId, true, collection.getLikeCount() + inserted);
    }

    /** 좋아요 취소 — 멱등 (좋아요 상태가 아니면 no-op). 실제 삭제 시에만 원자적 -1. */
    @Transactional
    public Map<String, Object> unlike(Long collectionId, String username) {
        User user = requireUser(username);
        Collection collection = requireCollection(collectionId);

        int deleted = collectionLikeRepository.deleteByCollectionIdAndUserId(collectionId, user.getId());
        if (deleted > 0) {
            collectionRepository.decrementLikeCount(collectionId);
        }
        return likeResponse(collectionId, false, Math.max(0, collection.getLikeCount() - deleted));
    }

    private Map<String, Object> likeResponse(Long collectionId, boolean liked, long likeCount) {
        return Map.of(
                "collectionId", collectionId,
                "liked", liked,
                "likeCount", likeCount
        );
    }

    // ========== 내부 헬퍼 ==========

    /** Page<Collection> → 카드 배치 보강(아이템 수·커버 포스터·likedByMe)까지 끝난 목록 응답 */
    private PageResponse<CollectionSummaryDTO> buildSummaryPage(Page<Collection> page, User viewer) {
        List<Collection> collections = page.getContent();
        List<CollectionSummaryDTO> dtos;
        if (collections.isEmpty()) {
            dtos = List.of();
        } else {
            List<Long> ids = collections.stream().map(Collection::getId).collect(Collectors.toList());
            Map<Long, Long> itemCounts = toCountMap(collectionItemRepository.countByCollectionIds(ids));
            Map<Long, List<String>> covers = toCoverMap(collectionItemRepository.findCoverPosters(ids));
            Set<Long> liked = viewer == null ? Set.of()
                    : new HashSet<>(collectionLikeRepository.findLikedCollectionIds(viewer.getId(), ids));

            dtos = collections.stream()
                    .map(c -> toSummary(c,
                            itemCounts.getOrDefault(c.getId(), 0L),
                            covers.getOrDefault(c.getId(), List.of()),
                            liked.contains(c.getId())))
                    .collect(Collectors.toList());
        }

        return PageResponse.<CollectionSummaryDTO>builder()
                .content(dtos)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }

    private CollectionSummaryDTO toSummary(Collection c, long itemCount,
                                           List<String> coverPosters, boolean likedByMe) {
        return CollectionSummaryDTO.builder()
                .id(c.getId())
                .title(c.getTitle())
                .description(c.getDescription())
                .domain(c.getDomain().name())
                .tint(c.getTint().name())
                .visibility(c.getVisibility().name())
                .likeCount(c.getLikeCount())
                .viewCount(c.getViewCount())
                .itemCount(itemCount)
                .curatorNickname(c.getUser().getUsername())
                .coverPosters(coverPosters)
                .likedByMe(likedByMe)
                .createdAt(c.getCreatedAt())
                .build();
    }

    private List<String> coverPostersOf(Long collectionId) {
        return toCoverMap(collectionItemRepository.findCoverPosters(List.of(collectionId)))
                .getOrDefault(collectionId, List.of());
    }

    private static Map<Long, Long> toCountMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return map;
    }

    private static Map<Long, List<String>> toCoverMap(List<Object[]> rows) {
        Map<Long, List<String>> map = new LinkedHashMap<>();
        for (Object[] row : rows) {
            Long collectionId = ((Number) row[0]).longValue();
            String posterUrl = (String) row[1];
            if (posterUrl != null && !posterUrl.isBlank()) {
                map.computeIfAbsent(collectionId, k -> new ArrayList<>(3)).add(posterUrl);
            }
        }
        return map;
    }

    private User requireUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "사용자를 찾을 수 없습니다: " + username));
    }

    private User findUserOrNull(String username) {
        if (username == null) return null;
        return userRepository.findByUsername(username).orElse(null);
    }

    private Collection requireCollection(Long collectionId) {
        return collectionRepository.findById(collectionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "컬렉션을 찾을 수 없습니다: " + collectionId));
    }

    /** 소유자 검증 — 수정/삭제/아이템 조작 공용 (소유자 아니면 403) */
    private Collection requireOwnedCollection(Long collectionId, String username) {
        Collection collection = requireCollection(collectionId);
        User user = requireUser(username);
        if (!isOwner(collection, user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "컬렉션 소유자만 가능합니다.");
        }
        return collection;
    }

    private static boolean isOwner(Collection collection, User user) {
        return user != null && collection.getUser().getId().equals(user.getId());
    }

    /** PRIVATE 컬렉션 접근 제어 — 소유자 외 403 */
    private static void requireVisible(Collection collection, boolean owner) {
        if (collection.getVisibility() == Collection.Visibility.PRIVATE && !owner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "비공개 컬렉션입니다.");
        }
    }

    private CollectionItem requireItemInCollection(Long collectionId, Long itemId) {
        CollectionItem item = collectionItemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "아이템을 찾을 수 없습니다: " + itemId));
        if (!item.getCollection().getId().equals(collectionId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 컬렉션의 아이템이 아닙니다.");
        }
        return item;
    }

    // ========== 검증 ==========

    /** 정렬 화이트리스트 — popular(기본)/latest 외 값은 400 (domain 파라미터와 일관) */
    private static Sort parseSort(String sort) {
        if (sort == null || sort.isBlank() || "popular".equalsIgnoreCase(sort)) {
            return Sort.by(Sort.Order.desc("likeCount"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        }
        if ("latest".equalsIgnoreCase(sort)) {
            return Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "지원하지 않는 정렬입니다: " + sort + " (popular | latest)");
    }

    private static String validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "제목은 필수입니다.");
        }
        String trimmed = title.trim();
        if (trimmed.length() > 60) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "제목은 60자 이내여야 합니다.");
        }
        return trimmed;
    }

    private static String validateDescription(String description) {
        if (description == null) return null;
        if (description.length() > 300) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "설명은 300자 이내여야 합니다.");
        }
        return description;
    }

    private static String validateComment(String comment) {
        if (comment == null) return null;
        if (comment.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "코멘트는 200자 이내여야 합니다.");
        }
        return comment;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, boolean required, String field) {
        if (value == null || value.isBlank()) {
            if (required) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + "은(는) 필수입니다.");
            }
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "지원하지 않는 " + field + " 값입니다: " + value);
        }
    }
}
