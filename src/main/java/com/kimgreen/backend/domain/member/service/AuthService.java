package com.kimgreen.backend.domain.member.service;

import com.kimgreen.backend.config.Authentication.JwtProvider;
import com.kimgreen.backend.domain.member.dto.Auth.ChangePasswordDto;
import com.kimgreen.backend.domain.member.dto.Auth.LogInRequestDto;
import com.kimgreen.backend.domain.member.dto.Auth.SignUpRequestDto;
import com.kimgreen.backend.domain.member.dto.Auth.TokenDto;
import com.kimgreen.backend.domain.member.entity.Member;
import com.kimgreen.backend.domain.member.entity.RefreshToken;
import com.kimgreen.backend.domain.member.repository.MemberProfileImgRepository;
import com.kimgreen.backend.domain.member.repository.MemberRepository;
import com.kimgreen.backend.domain.member.repository.RefreshTokenRepository;
import com.kimgreen.backend.exception.DuplicateEmail;
import com.kimgreen.backend.exception.LogInFailureEmail;
import com.kimgreen.backend.exception.LogInFailurePassword;
import com.kimgreen.backend.exception.RefreshTokenExpired;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberRepository memberRepository;
    private final MemberProfileImgRepository profileImgRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final AuthenticationManagerBuilder authenticationManagerBuilder;
    private final MemberService memberService;
    private static final String SUCCESS = "success";
    private static final String EXPIRED = "expired";

    public void signUp(SignUpRequestDto signUpRequestDto) {
        String email = signUpRequestDto.getEmail();
        String password = signUpRequestDto.getPassword();
        String nickname = signUpRequestDto.getNickname();

        validateEmail(email);
        memberRepository.save(signUpRequestDto.toMemberEntity(email, passwordEncoder.encode(password),nickname));
        profileImgRepository.save(signUpRequestDto.toMemberProfileImgEntity(memberRepository.findByEmail(email)));
    }

    @Transactional
    public TokenDto logIn(LogInRequestDto dto) {
        String email = dto.getEmail();
        if(!(memberRepository.existsByEmail(email))) {
            throw new LogInFailureEmail();
        }
        Member member = memberRepository.findByEmail(dto.getEmail());
        checkPassword(dto.getPassword(), member.getPassword());


        // user 검증
        Authentication authentication = setAuthentication(dto);
        // token 생성
        String accessToken = jwtProvider.generateAccessToken(authentication);
        String refreshToken = jwtProvider.generateRefreshToken(authentication);
        User user = (User) authentication.getPrincipal(); // user 정보
        RefreshToken generatedRefreshToken = RefreshToken.builder()
                .refreshToken(refreshToken)
                .email(memberRepository.findByEmail(email).getEmail())
                .build();
        // refresh token 저장
        saveRefreshToken(email, generatedRefreshToken);


        return TokenDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();

    }

    public TokenDto tokenReissue(TokenDto tokenDto) {
        //어차피 accessToken 만료인 경우에 호출되기 때문에 AT는 검증할 필요X
        String accessToken = tokenDto.getAccessToken();
        String refreshToken = tokenDto.getRefreshToken();
        Authentication authentication = jwtProvider.getAuthentication(refreshToken);
        String email = authentication.getName();

        // refresh token 검증
        if (StringUtils.hasText(refreshToken) && jwtProvider.validateToken(refreshToken) == SUCCESS) {
            System.out.println("getting new access token");
            // access token 재발급
            String newAccessToken = jwtProvider.generateAccessToken(authentication);

            System.out.println("Reissue access token success");
            return tokenDto.builder()
                    .refreshToken(refreshToken)
                    .accessToken(newAccessToken)
                    .build();
        } else { //refresh token 만료
            refreshTokenRepository.deleteByEmail(email);
            //RT 만료됐다는걸 알리는 예외 발생 -> 로그인으로 유도
            throw new RefreshTokenExpired();
        }
    }

    @Transactional
    public void changePassword(ChangePasswordDto dto) {
        Member member = memberService.getCurrentMember();
        checkPassword(dto.getPasswordToCheck(), member.getPassword());
        member.changePassword(passwordEncoder.encode(dto.getNewPassword()));
    }

    public void checkPassword(String getPassword, String password) {
        if (!(passwordEncoder.matches(getPassword, password))) {
            throw new LogInFailurePassword();
        }
    }

    public Authentication setAuthentication(LogInRequestDto dto) {
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(dto.getEmail(), dto.getPassword());
        Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return authentication;
    }
    public void saveRefreshToken(String email, RefreshToken generatedRefreshToken) {
        if (!refreshTokenRepository.existsByEmail(email)) {
            refreshTokenRepository.save(generatedRefreshToken);
        } else {
            refreshTokenRepository.findByEmail(email).updateRefreshToken(generatedRefreshToken.getRefreshToken());
        }
    }

    public void validateEmail(String email) {
        if(memberRepository.existsByEmail(email)) {
            throw new DuplicateEmail();
        }
    }

}
