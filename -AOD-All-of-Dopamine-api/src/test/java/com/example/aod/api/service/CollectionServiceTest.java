package com.example.AOD.api.service;

import com.example.AOD.api.dto.WorkSummaryDTO;
import com.example.AOD.api.dto.collection.CollectionCreateRequest;
import com.example.AOD.api.dto.collection.CollectionDetailDTO;
import com.example.AOD.api.dto.collection.CollectionItemAddRequest;
import com.example.AOD.api.dto.collection.CollectionItemDTO;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * CollectionService 단위 테스트 (Mockito).
 * 플랜 필수 케이스: 좋아요 멱등 / 중복 409 / PRIVATE 403 / 도메인 불일치 400
 */
@ExtendWith(MockitoExtension.class)
class CollectionServiceTest {

    @Mock private CollectionRepository collectionRepository;
    @Mock private CollectionItemRepository collectionItemRepository;
    @Mock private CollectionLikeRepository collectionLikeRepository;
    @Mock private ContentRepository contentRepository;
    @Mock private UserRepository userRepository;
    @Mock private WorkApiService workApiService;

    @InjectMocks private CollectionService collectionService;

    // ===== 헬퍼 =====

    private User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }

    private Collection collection(Long id, User owner, Domain domain, Collection.Visibility visibility) {
        Collection collection = new Collection();
        collection.setId(id);
        collection.setUser(owner);
        collection.setTitle("테스트 컬렉션");
        collection.setDomain(domain);
        collection.setVisibility(visibility);
        return collection;
    }

    private Content content(Long id, Domain domain) {
        Content content = new Content();
        content.setContentId(id);
        content.setDomain(domain);
        content.setMasterTitle("테스트 작품");
        return content;
    }

    private static ResponseStatusException statusOf(Runnable runnable) {
        try {
            runnable.run();
        } catch (ResponseStatusException e) {
            return e;
        }
        throw new AssertionError("ResponseStatusException이 발생해야 합니다.");
    }

    // ===== 아이템 추가 =====

    @Test
    @DisplayName("아이템 추가 - 컬렉션 도메인과 작품 도메인 불일치면 400")
    void addItem_domainMismatch_returns400() {
        User owner = user(1L, "curator");
        Collection collection = collection(10L, owner, Domain.GAME, Collection.Visibility.PUBLIC);
        given(collectionRepository.findById(10L)).willReturn(Optional.of(collection));
        given(userRepository.findByUsername("curator")).willReturn(Optional.of(owner));
        given(contentRepository.findById(100L)).willReturn(Optional.of(content(100L, Domain.MOVIE)));

        ResponseStatusException e = statusOf(() ->
                collectionService.addItem(10L, "curator", new CollectionItemAddRequest(100L, null)));

        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(collectionItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("아이템 추가 - 이미 담긴 작품이면 409")
    void addItem_duplicate_returns409() {
        User owner = user(1L, "curator");
        Collection collection = collection(10L, owner, Domain.GAME, Collection.Visibility.PUBLIC);
        given(collectionRepository.findById(10L)).willReturn(Optional.of(collection));
        given(userRepository.findByUsername("curator")).willReturn(Optional.of(owner));
        given(contentRepository.findById(100L)).willReturn(Optional.of(content(100L, Domain.GAME)));
        given(collectionItemRepository.existsByCollectionIdAndContentContentId(10L, 100L)).willReturn(true);

        ResponseStatusException e = statusOf(() ->
                collectionService.addItem(10L, "curator", new CollectionItemAddRequest(100L, null)));

        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(collectionItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("아이템 추가 - position은 말미(최대값+1), 첫 아이템은 0")
    void addItem_appendsToTail() {
        User owner = user(1L, "curator");
        Collection collection = collection(10L, owner, Domain.GAME, Collection.Visibility.PUBLIC);
        Content content = content(100L, Domain.GAME);
        given(collectionRepository.findById(10L)).willReturn(Optional.of(collection));
        given(userRepository.findByUsername("curator")).willReturn(Optional.of(owner));
        given(contentRepository.findById(100L)).willReturn(Optional.of(content));
        given(collectionItemRepository.existsByCollectionIdAndContentContentId(10L, 100L)).willReturn(false);
        given(collectionItemRepository.findMaxPosition(10L)).willReturn(4);
        given(collectionItemRepository.save(any(CollectionItem.class))).willAnswer(inv -> {
            CollectionItem item = inv.getArgument(0);
            item.setId(77L);
            return item;
        });
        given(workApiService.toEnrichedSummaries(anyList())).willReturn(List.of(
                WorkSummaryDTO.builder().id(100L).domain("GAME").title("테스트 작품").build()));

        CollectionItemDTO dto = collectionService.addItem(10L, "curator",
                new CollectionItemAddRequest(100L, "말미 코멘트"));

        ArgumentCaptor<CollectionItem> captor = ArgumentCaptor.forClass(CollectionItem.class);
        verify(collectionItemRepository).save(captor.capture());
        assertThat(captor.getValue().getPosition()).isEqualTo(5); // 말미 = max(4) + 1
        assertThat(dto.getItemId()).isEqualTo(77L);
        assertThat(dto.getContentId()).isEqualTo(100L);
        assertThat(dto.getComment()).isEqualTo("말미 코멘트");
    }

    @Test
    @DisplayName("아이템 추가 - 소유자가 아니면 403")
    void addItem_notOwner_returns403() {
        User owner = user(1L, "curator");
        User stranger = user(2L, "stranger");
        Collection collection = collection(10L, owner, Domain.GAME, Collection.Visibility.PUBLIC);
        given(collectionRepository.findById(10L)).willReturn(Optional.of(collection));
        given(userRepository.findByUsername("stranger")).willReturn(Optional.of(stranger));

        ResponseStatusException e = statusOf(() ->
                collectionService.addItem(10L, "stranger", new CollectionItemAddRequest(100L, null)));

        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ===== PRIVATE 접근 제어 =====

    @Test
    @DisplayName("상세 조회 - PRIVATE 컬렉션은 소유자 외 403 (조회수 미증가)")
    void detail_private_nonOwner_returns403() {
        User owner = user(1L, "curator");
        User stranger = user(2L, "stranger");
        Collection collection = collection(10L, owner, Domain.WEBTOON, Collection.Visibility.PRIVATE);
        given(collectionRepository.findById(10L)).willReturn(Optional.of(collection));
        given(userRepository.findByUsername("stranger")).willReturn(Optional.of(stranger));

        ResponseStatusException e = statusOf(() ->
                collectionService.getCollectionDetail(10L, "stranger"));

        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(collectionRepository, never()).incrementViewCount(any());
    }

    @Test
    @DisplayName("상세 조회 - PRIVATE 컬렉션은 비로그인도 403")
    void detail_private_anonymous_returns403() {
        User owner = user(1L, "curator");
        Collection collection = collection(10L, owner, Domain.WEBTOON, Collection.Visibility.PRIVATE);
        given(collectionRepository.findById(10L)).willReturn(Optional.of(collection));

        ResponseStatusException e = statusOf(() ->
                collectionService.getCollectionDetail(10L, null));

        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(collectionRepository, never()).incrementViewCount(any());
    }

    @Test
    @DisplayName("상세 조회 - 소유자는 PRIVATE도 조회 가능, 조회수 원자적 +1")
    void detail_private_owner_ok_and_viewCountIncremented() {
        User owner = user(1L, "curator");
        Collection collection = collection(10L, owner, Domain.WEBTOON, Collection.Visibility.PRIVATE);
        collection.setViewCount(41L);
        given(collectionRepository.findById(10L)).willReturn(Optional.of(collection));
        given(userRepository.findByUsername("curator")).willReturn(Optional.of(owner));
        given(collectionItemRepository.findByCollectionIdOrderByPositionAscIdAsc(10L)).willReturn(List.of());
        given(workApiService.toEnrichedSummaries(anyList())).willReturn(List.of());
        given(collectionLikeRepository.existsByCollectionIdAndUserId(10L, 1L)).willReturn(false);

        CollectionDetailDTO dto = collectionService.getCollectionDetail(10L, "curator");

        verify(collectionRepository).incrementViewCount(10L);
        assertThat(dto.getViewCount()).isEqualTo(42L); // 이번 조회분 +1 반영
        assertThat(dto.isOwner()).isTrue();
    }

    // ===== 좋아요 멱등 =====

    @Test
    @DisplayName("좋아요 - 신규 삽입 시에만 like_count 원자적 +1")
    void like_firstTime_incrementsCount() {
        User owner = user(1L, "curator");
        User fan = user(2L, "fan");
        Collection collection = collection(10L, owner, Domain.GAME, Collection.Visibility.PUBLIC);
        collection.setLikeCount(5);
        given(userRepository.findByUsername("fan")).willReturn(Optional.of(fan));
        given(collectionRepository.findById(10L)).willReturn(Optional.of(collection));
        given(collectionLikeRepository.insertIfAbsent(10L, 2L)).willReturn(1);

        Map<String, Object> response = collectionService.like(10L, "fan");

        verify(collectionRepository).incrementLikeCount(10L);
        assertThat(response.get("liked")).isEqualTo(true);
        assertThat(response.get("likeCount")).isEqualTo(6L);
    }

    @Test
    @DisplayName("좋아요 - 이미 좋아요 상태면 멱등(no-op), 카운트 미증가")
    void like_alreadyLiked_isIdempotent() {
        User owner = user(1L, "curator");
        User fan = user(2L, "fan");
        Collection collection = collection(10L, owner, Domain.GAME, Collection.Visibility.PUBLIC);
        collection.setLikeCount(5);
        given(userRepository.findByUsername("fan")).willReturn(Optional.of(fan));
        given(collectionRepository.findById(10L)).willReturn(Optional.of(collection));
        given(collectionLikeRepository.insertIfAbsent(10L, 2L)).willReturn(0);

        Map<String, Object> response = collectionService.like(10L, "fan");

        verify(collectionRepository, never()).incrementLikeCount(any());
        assertThat(response.get("liked")).isEqualTo(true);
        assertThat(response.get("likeCount")).isEqualTo(5L);
    }

    @Test
    @DisplayName("좋아요 - PRIVATE 컬렉션은 소유자 외 403")
    void like_private_nonOwner_returns403() {
        User owner = user(1L, "curator");
        User fan = user(2L, "fan");
        Collection collection = collection(10L, owner, Domain.GAME, Collection.Visibility.PRIVATE);
        given(userRepository.findByUsername("fan")).willReturn(Optional.of(fan));
        given(collectionRepository.findById(10L)).willReturn(Optional.of(collection));

        ResponseStatusException e = statusOf(() -> collectionService.like(10L, "fan"));

        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(collectionLikeRepository, never()).insertIfAbsent(any(), any());
    }

    @Test
    @DisplayName("좋아요 취소 - 실제 삭제 시에만 like_count 원자적 -1")
    void unlike_whenLiked_decrementsCount() {
        User owner = user(1L, "curator");
        User fan = user(2L, "fan");
        Collection collection = collection(10L, owner, Domain.GAME, Collection.Visibility.PUBLIC);
        collection.setLikeCount(5);
        given(userRepository.findByUsername("fan")).willReturn(Optional.of(fan));
        given(collectionRepository.findById(10L)).willReturn(Optional.of(collection));
        given(collectionLikeRepository.deleteByCollectionIdAndUserId(10L, 2L)).willReturn(1);

        Map<String, Object> response = collectionService.unlike(10L, "fan");

        verify(collectionRepository).decrementLikeCount(10L);
        assertThat(response.get("liked")).isEqualTo(false);
        assertThat(response.get("likeCount")).isEqualTo(4L);
    }

    @Test
    @DisplayName("좋아요 취소 - 좋아요 상태가 아니면 멱등(no-op), 카운트 미감소")
    void unlike_notLiked_isIdempotent() {
        User owner = user(1L, "curator");
        User fan = user(2L, "fan");
        Collection collection = collection(10L, owner, Domain.GAME, Collection.Visibility.PUBLIC);
        collection.setLikeCount(5);
        given(userRepository.findByUsername("fan")).willReturn(Optional.of(fan));
        given(collectionRepository.findById(10L)).willReturn(Optional.of(collection));
        given(collectionLikeRepository.deleteByCollectionIdAndUserId(10L, 2L)).willReturn(0);

        Map<String, Object> response = collectionService.unlike(10L, "fan");

        verify(collectionRepository, never()).decrementLikeCount(any());
        assertThat(response.get("likeCount")).isEqualTo(5L);
    }

    // ===== 생성 검증 =====

    @Test
    @DisplayName("생성 - 제목 없으면 400")
    void create_blankTitle_returns400() {
        given(userRepository.findByUsername("curator")).willReturn(Optional.of(user(1L, "curator")));

        ResponseStatusException e = statusOf(() -> collectionService.createCollection("curator",
                new CollectionCreateRequest("  ", null, "GAME", null, null)));

        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(collectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("생성 - 지원하지 않는 domain 값이면 400")
    void create_invalidDomain_returns400() {
        given(userRepository.findByUsername("curator")).willReturn(Optional.of(user(1L, "curator")));

        ResponseStatusException e = statusOf(() -> collectionService.createCollection("curator",
                new CollectionCreateRequest("내 컬렉션", null, "MUSIC", null, null)));

        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(collectionRepository, never()).save(any());
    }

    // ===== 재정렬 =====

    @Test
    @DisplayName("재정렬 - itemIds가 컬렉션 아이템과 불일치하면 400")
    void reorder_mismatchedIds_returns400() {
        User owner = user(1L, "curator");
        Collection collection = collection(10L, owner, Domain.GAME, Collection.Visibility.PUBLIC);
        given(collectionRepository.findById(10L)).willReturn(Optional.of(collection));
        given(userRepository.findByUsername("curator")).willReturn(Optional.of(owner));

        CollectionItem item = new CollectionItem();
        item.setId(1L);
        item.setCollection(collection);
        item.setPosition(0);
        given(collectionItemRepository.findByCollectionIdOrderByPositionAscIdAsc(10L))
                .willReturn(List.of(item));

        ResponseStatusException e = statusOf(() ->
                collectionService.reorderItems(10L, "curator", List.of(1L, 999L)));

        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(collectionItemRepository, never()).saveAll(any());
    }
}
