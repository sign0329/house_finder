package kr.aptfolio.common.security;

import java.util.Arrays;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Run in a terminal: never pass a password as an argument. */
public final class AdminPasswordHash {
 public static void main(String[] args) {
  var console=System.console();
  if(console==null)throw new IllegalStateException("터미널에서 실행하세요.");
  char[] password=console.readPassword("관리자 비밀번호 (12자 이상, UTF-8 72바이트 이하): ");
  char[] confirm=console.readPassword("비밀번호 확인: ");
  try {
   if(password==null||confirm==null||!Arrays.equals(password,confirm)||password.length<12||new String(password).getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)
    throw new IllegalArgumentException("비밀번호 길이 또는 확인 입력을 확인하세요.");
   console.printf("%s%n",new BCryptPasswordEncoder(12).encode(new String(password)));
  } finally {if(password!=null)Arrays.fill(password,'\0');if(confirm!=null)Arrays.fill(confirm,'\0');}
 }
}
