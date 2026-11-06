package kr.aptfolio.trade;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
@Component
public class KakaoMapSettings {
 private final String key;
 public KakaoMapSettings(@Value("${KAKAO_MAP_JAVASCRIPT_KEY:}") String key){this.key=key.trim();}
 public boolean enabled(){return !key.isEmpty();}
 public String key(){return key;}
}
