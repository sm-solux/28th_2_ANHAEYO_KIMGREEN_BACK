package com.kimgreen.backend.domain.community.service;

import com.kimgreen.backend.domain.community.dto.GetPostInfoResponseDto;
import com.kimgreen.backend.domain.community.dto.WritePostRequestDto;
import com.kimgreen.backend.domain.community.entity.*;
import com.kimgreen.backend.domain.community.repository.PostImgRepository;
import com.kimgreen.backend.domain.community.repository.PostRepository;
import com.kimgreen.backend.domain.member.entity.Member;
import com.kimgreen.backend.domain.member.entity.MemberProfileImg;
import com.kimgreen.backend.domain.member.repository.MemberProfileImgRepository;
import com.kimgreen.backend.domain.member.service.MemberService;
import com.kimgreen.backend.domain.profile.entity.Badge;
import com.kimgreen.backend.domain.profile.entity.RepresentativeBadge;
import com.kimgreen.backend.domain.profile.repository.BadgeRepository;
import com.kimgreen.backend.domain.profile.repository.RepresentativeBadgeRepository;
import com.kimgreen.backend.exception.PostNotFound;
import lombok.RequiredArgsConstructor;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Arrays.stream;

@Service
@RequiredArgsConstructor
public class PostService {

    private final S3Service s3Service;
    private final MemberService memberService;
    private final PostRepository postRepository;
    private final PostImgRepository postImgRepository;
    private final MemberProfileImgRepository memberProfileImgRepository;
    private final RepresentativeBadgeRepository representativeBadgeRepository;
    private final BadgeRepository badgeRepository;

    //게시물 작성
    @Transactional
    public void writeCheckPost(WritePostRequestDto writePostRequestDto, MultipartFile multipartFile, Member member) throws IOException {

        Badge badge = badgeRepository.findByMember(member);
        Post post = postRepository.save(writePostRequestDto.toCertifyPostEntity(
                writePostRequestDto.getCategory(),
                writePostRequestDto.getContent(), member));

        uploadPostFileList(multipartFile, post);
        updateBadgeCount(badge, post.getCategory(), post.getTag());
    }

    @Transactional
    public void writeDailyPost(WritePostRequestDto writePostRequestDto, MultipartFile multipartFile, Member member) throws IOException {

        Badge badge = badgeRepository.findByMember(member);
        Post post = postRepository.save(writePostRequestDto.toDailyPostEntity(
                writePostRequestDto.getCategory(),
                writePostRequestDto.getContent(), member));

        if (multipartFile != null) {
            uploadPostFileList(multipartFile, post);
        }
        updateBadgeCount(badge, post.getCategory(), post.getTag());
    }

    @Transactional
    public void uploadPostFileList(MultipartFile multipartFile, Post post) throws IOException {
        PostImg postImg = postImgRepository.save(PostImg.builder()
                .imgUrl(s3Service.saveFile(multipartFile))
                .title(multipartFile.getOriginalFilename())
                .post(post).build());
    }

    //뱃지 카운트
    private void updateBadgeCount(Badge badge, Category category, Tag tag) {

        LocalDateTime currentTime = LocalDateTime.now();
        boolean isBefore9AM = currentTime.toLocalTime().isBefore(LocalTime.of(9, 0));

        // 아침 9시 전에 인증 게시글이 작성된 경우
        if (isBefore9AM && tag.equals(Tag.CERTIFY)) {
            badge.setEarlybirdCount(badge.getEarlybirdCount() + 1);
        }

        if (tag.equals(Tag.CERTIFY)){
            badge.setCertificationCount(badge.getCertificationCount() +1);
            if (category.equals(Category.RECEIPT)) { badge.setReceiptCount(badge.getReceiptCount() + 1);}
            if (category.equals(Category.REUSABLE)) { badge.setReusableCount(badge.getReusableCount() + 1);}
            if (category.equals(Category.PLASTIC)) { badge.setPlasticCount(badge.getPlasticCount() + 1);}
            if (category.equals(Category.PLOGGING)) { badge.setPloggingCount(badge.getPloggingCount() + 1);}
            if (category.equals(Category.REFORM)) { badge.setReformCount(badge.getReformCount() + 1);}
            if (category.equals(Category.TRANSPORT)) { badge.setTransportCount(badge.getTransportCount() + 1);}
            if (category.equals(Category.ETC)) { badge.setEtcCount(badge.getEtcCount() + 1);}
            if (category.equals(Category.QUESTION)) { badge.setMentorCount(badge.getMentorCount() + 1);}
        }
    }


    //뱃지조건에 따라 isAchieved update ->
    // + 삭제했을 떄 처리 코드도 작성



    // 게시글 상세정보 조회
    @Transactional
    public GetPostInfoResponseDto getPostInfo(Long postId) {
        Post post = postRepository.findById(postId).orElseThrow(PostNotFound::new);
        Member member = post.getMember();

        List<Likes> likeList = post.getLikes();
        boolean isLiked = isLiked(likeList,member);

        if(post.getPostImg()!= null) {
            return GetPostInfoResponseDto.builder()
                    .writerNickname(post.getMember().getNickname())
                    .writerProfileImg(s3Service.getFullUrl(memberProfileImgRepository.findByMember(member).getImgUrl()))
                    .imgUrl(s3Service.getFullUrl(postImgRepository.findByPost(post).getImgUrl()))
                    .content(post.getContent())
                    .postId(post.getPostId())
                    .category(String.valueOf(post.getCategory()))
                    .writerBadge(representativeBadgeRepository.findByMember(post.getMember()).getRepresentativeBadge().name)
                    .likeCount(post.getLikes().size())
                    .commentCount(post.getComments().size())
                    .isLiked(isLiked)
                    .isMine(post.getMember().getMemberId().equals(memberService.getCurrentMember().getMemberId()))
                    .updatedAt(String.valueOf(post.getModifiedAt()))
                    .build();
        }
        return GetPostInfoResponseDto.builder()
                .writerNickname(post.getMember().getNickname())
                .writerProfileImg(s3Service.getFullUrl(memberProfileImgRepository.findByMember(member).getImgUrl()))
                .content(post.getContent())
                .postId(post.getPostId())
                .category(String.valueOf(post.getCategory()))
                .writerBadge(representativeBadgeRepository.findByMember(post.getMember()).getRepresentativeBadge().name)
                .likeCount(post.getLikes().size())
                .commentCount(post.getComments().size())
                .isLiked(isLiked)
                .isMine(post.getMember().getMemberId().equals(memberService.getCurrentMember().getMemberId()))
                .updatedAt(String.valueOf(post.getModifiedAt()))
                .build();
    }

    @Transactional
    public List<GetPostInfoResponseDto> getPostlist(Category category, Tag tag) {

        List<Post> posts = postRepository.findAll();
        List<GetPostInfoResponseDto> postList = new ArrayList<>();

        for (Post post : posts) {

            if (post.getTag() != Tag.DAILY || !post.getCategory().equals(category)) {
                continue;
            }

            Member member = post.getMember();
            List<Likes> likeList = post.getLikes();
            boolean isLiked = isLiked(likeList, member);

            Optional<PostImg> optionalPostImg = Optional.ofNullable(postImgRepository.findByPost(post));
            String imgUrl = optionalPostImg.map(img -> s3Service.getFullUrl(img.getImgUrl())).orElse("defaultImageUrl");

            GetPostInfoResponseDto getPostInfoResponseDto = GetPostInfoResponseDto.builder()
                    .writerNickname(post.getMember().getNickname())
                    .writerProfileImg(s3Service.getFullUrl(memberProfileImgRepository.findByMember(member).getImgUrl()))
                    .content(post.getContent())
                    .postId(post.getPostId())
                    .category(String.valueOf(post.getCategory()))
                    .writerBadge(representativeBadgeRepository.findByMember(member).getRepresentativeBadge().name)
                    .likeCount(post.getLikes().size())
                    .commentCount(post.getComments().size())
                    .isLiked(isLiked)
                    .isMine(post.getMember().getMemberId().equals(memberService.getCurrentMember().getMemberId()))
                    .updatedAt(String.valueOf(post.getModifiedAt()))
                    .build();

            if (post.getPostImg() != null) {
                getPostInfoResponseDto.setImgUrl(s3Service.getFullUrl(postImgRepository.findByPost(post).getImgUrl()));
            }
            postList.add(getPostInfoResponseDto);
        }
        return postList;
    }

    //게시글 삭제하기
    @Transactional
    public void deletePost(Long postId) {
        Member member = memberService.getCurrentMember();
        Post post = postRepository.findById(postId).orElseThrow(PostNotFound::new);
        // 게시글 삭제
        postRepository.delete(post);
    }

    //게시글 수정하기
    @Transactional
    public void editPost(Long postId, WritePostRequestDto editPostInfoRequestDto, MultipartFile
            multipartFile) throws IOException {

        Member member = memberService.getCurrentMember();
        Post post = postRepository.findById(postId).orElseThrow(PostNotFound::new);

        post.update(editPostInfoRequestDto.getCategory(), editPostInfoRequestDto.getContent());

        if (multipartFile != null) {
            uploadPostFileList(multipartFile, post);
        }
    }

    public boolean isLiked(List<Likes> likesList,Member member) {
        for(Likes like : likesList) {
            if(like.getLikeId().equals(member.getMemberId())) {
                return true;
            }
        }
        return false;
    }

    //좋아요 상위 목록 불러오기
    @JsonIgnore
    @Transactional
    public List<Post> getBestPostList() {
        List<Post> bestPost = postRepository.findTop3ByOrderByLikeCountDesc();
        List<GetPostInfoResponseDto> postList = new ArrayList<>();

        for (Post post : bestPost) {

            Member member = post.getMember();
            List<Likes> likeList = post.getLikes();
            boolean isLiked = isLiked(likeList, member);

            Optional<PostImg> optionalPostImg = Optional.ofNullable(postImgRepository.findByPost(post));
            String imgUrl = optionalPostImg.map(img -> s3Service.getFullUrl(img.getImgUrl())).orElse("defaultImageUrl");

            GetPostInfoResponseDto getPostInfoResponseDto = GetPostInfoResponseDto.builder()
                    .writerNickname(post.getMember().getNickname())
                    .writerProfileImg(s3Service.getFullUrl(memberProfileImgRepository.findByMember(member).getImgUrl()))
                    .content(post.getContent())
                    .postId(post.getPostId())
                    .category(String.valueOf(post.getCategory()))
                    .writerBadge(representativeBadgeRepository.findByMember(member).getRepresentativeBadge().name)
                    .likeCount(post.getLikes().size())
                    .commentCount(post.getComments().size())
                    .isLiked(isLiked)
                    .isMine(post.getMember().getMemberId().equals(memberService.getCurrentMember().getMemberId()))
                    .updatedAt(String.valueOf(post.getModifiedAt()))
                    .build();

            if (post.getPostImg() != null) {
                getPostInfoResponseDto.setImgUrl(s3Service.getFullUrl(postImgRepository.findByPost(post).getImgUrl()));
            }
            postList.add(getPostInfoResponseDto);
        }
        return bestPost;
    }
}