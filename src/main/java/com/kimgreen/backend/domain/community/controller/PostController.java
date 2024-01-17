package com.kimgreen.backend.domain.community.controller;

import com.kimgreen.backend.domain.community.dto.WritePostRequestDto;
import com.kimgreen.backend.domain.member.service.MemberService;
import com.kimgreen.backend.domain.community.service.PostService;
import com.kimgreen.backend.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


import java.io.IOException;

import static com.kimgreen.backend.response.Message.*;
import static com.kimgreen.backend.response.Response.*;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;

@Tag(name = "Post")
@RestController
@RequiredArgsConstructor
@RequestMapping(value="/post")

public class PostController {

    private final PostService postService;
    private final MemberService memberService;

    @Operation(summary = "게시글 작성(인증)")
    @ResponseStatus(OK)
    @PostMapping(path="/check", consumes = MULTIPART_FORM_DATA_VALUE)
    public Response writeCheckPost(@RequestBody WritePostRequestDto writePostRequestDto,
                                   @RequestPart(name = "files") MultipartFile multipartFiles) throws IOException { //파일 필수 O
        postService.writeCheckPost(writePostRequestDto, multipartFiles, memberService.getCurrentMember());
        return success(SUCCESS_TO_WRITE_POST);
    }

    @Operation(summary = "게시글 작성(일상)")
    @ResponseStatus(OK)
    @PostMapping(path="/daily", consumes = MULTIPART_FORM_DATA_VALUE)
    public Response writeDailyPost(@RequestPart(name = "body(json)") WritePostRequestDto writePostRequestDto,
                              @RequestPart(name = "files", required = false) MultipartFile multipartFiles) throws IOException { //파일 필수 X
        postService.writeDailyPost(writePostRequestDto, multipartFiles, memberService.getCurrentMember());
        return success(SUCCESS_TO_WRITE_POST);
    }
}