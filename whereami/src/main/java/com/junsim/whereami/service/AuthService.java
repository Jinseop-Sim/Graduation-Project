package com.junsim.whereami.service;

import com.junsim.whereami.domain.Member;
import com.junsim.whereami.dto.AuthTokenDTO;
import com.junsim.whereami.dto.EmailAuthDTO;
import com.junsim.whereami.dto.LoginDTO;
import com.junsim.whereami.dto.SignUpDTO;
import com.junsim.whereami.errors.exception.Exception400;
import com.junsim.whereami.errors.exception.Exception404;
import com.junsim.whereami.jwt.JwtTokenProvider;
import com.junsim.whereami.repository.MemberRepository;
import com.junsim.whereami.utility.RedisUtility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Random;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AuthService {
    private final MemberRepository memberRepository;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManagerBuilder authenticationManagerBuilder;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender javaMailSender;
    private final RedisUtility redisUtility;

    public void signUp(SignUpDTO signUpDTO){
        if(memberRepository.findByEmail(signUpDTO.getEmail()).isPresent())
            throw new Exception400("이미 존재하는 이메일입니다.");
        Member member = new Member(signUpDTO.getEmail(), passwordEncoder.encode(signUpDTO.getPassword()), signUpDTO.getNickName());
        memberRepository.save(member);
    }

    public AuthTokenDTO login(LoginDTO loginDTO){
        Optional<Member> loginMember = memberRepository.findByEmail(loginDTO.getEmail());
        String wrongCount = redisUtility.getValues(loginDTO.getEmail());
        if(loginMember.isEmpty())
            throw new Exception404("존재하지 않는 이메일입니다.");
        if(wrongCount != null && wrongCount.length() > 4)
            throw new Exception400("기능이 제한된 계정입니다.");
        if(!passwordEncoder.matches(loginDTO.getPassword(), loginMember.get().getPassword())){
            checkWrongPassword(loginDTO.getEmail());
            throw new Exception400("비밀번호가 " + redisUtility.getValues(loginDTO.getEmail()).length() + "회 틀렸습니다.");
        }

        UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken
                = new UsernamePasswordAuthenticationToken(loginDTO.getEmail(), loginDTO.getPassword());
        Authentication authentication = authenticationManagerBuilder.getObject().authenticate(usernamePasswordAuthenticationToken);
        AuthTokenDTO authTokenDto = tokenProvider.generateToken(authentication);

        return authTokenDto;
    }

    public void emailAuth(EmailAuthDTO emailAuthDTO) {

        if(redisUtility.getValues(emailAuthDTO.getEmail()).equals(emailAuthDTO.getAuthNum())){
            memberRepository.findByEmail(
                    SecurityContextHolder.getContext().getAuthentication().getName()).get().upgrade();
        }
    }

    public void sendEmail(String email) {
        Integer authNumber = makeNum();
        redisUtility.setValues(email, Integer.toString(authNumber), 300);
        SimpleMailMessage simpleMailMessage = new SimpleMailMessage();
        simpleMailMessage.setTo(email);
        simpleMailMessage.setSubject("뭐더라!");
        simpleMailMessage.setText("인증 번호 입니다.\n"
                + authNumber +
                "\n잘 입력해 보세요!");
        // 이메일 발신
        javaMailSender.send(simpleMailMessage);
    }

    public void printAuth(){
        System.out.println(SecurityContextHolder.getContext().getAuthentication().getName() + " : " + memberRepository.findByEmail(SecurityContextHolder.getContext().getAuthentication().getName()).get().getAuthority());
    }

    public Integer makeNum() {
        return new Random().nextInt(888888) + 111111;
    }
    public void checkWrongPassword(String email) {
        if(redisUtility.getValues(email) == null)
            redisUtility.setValues(email, "1");
        else
            redisUtility.setValues(email, redisUtility.getValues(email) + "1");
    }
}
