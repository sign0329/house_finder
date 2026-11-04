package kr.aptfolio.account;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserAccounts {
 private final NamedParameterJdbcTemplate db;
 private final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder(12);
 public UserAccounts(NamedParameterJdbcTemplate db){this.db=db;}
 public record Profile(String username,String nickname,LocalDateTime createdAt){}
 public void register(String username,String nickname,String password,String confirm) {
  if(username==null||!username.matches("[a-z0-9_]{4,30}"))throw new IllegalArgumentException("아이디는 영문 소문자·숫자·밑줄 4~30자로 입력하세요.");
  if(nickname==null||nickname.strip().length()<2||nickname.strip().length()>30||nickname.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("닉네임은 2~30자로 입력하세요.");
  if(password==null||password.length()<12||password.length()>72||password.getBytes(StandardCharsets.UTF_8).length>72)throw new IllegalArgumentException("비밀번호는 12자 이상, UTF-8 72바이트 이하로 입력하세요.");
  if(!password.equals(confirm))throw new IllegalArgumentException("비밀번호 확인이 일치하지 않습니다.");
  db.update("insert into user_account(username,nickname,password_hash) values(:username,:nickname,:hash)",Map.of("username",username,"nickname",nickname.strip(),"hash",encoder.encode(password)));
 }
 public UserDetails loadUserByUsername(String username) {
  return db.query("select username,password_hash from user_account where username=:username",Map.of("username",username),
   (r,n)->User.withUsername(r.getString("username")).password(r.getString("password_hash")).roles("USER").build())
   .stream().findFirst().orElseThrow(()->new UsernameNotFoundException("사용자를 찾을 수 없습니다."));
 }
 public Profile profile(String username) {
  return db.query("select username,nickname,created_at from user_account where username=:username",Map.of("username",username),
   (r,n)->new Profile(r.getString("username"),r.getString("nickname"),r.getTimestamp("created_at").toLocalDateTime()))
   .stream().findFirst().orElseThrow(()->new UsernameNotFoundException("사용자를 찾을 수 없습니다."));
 }
}
