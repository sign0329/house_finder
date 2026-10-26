package kr.aptfolio.molit;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="molit.import-on-start",havingValue="true")
public class MolitImportRunner implements CommandLineRunner {
 private static final Logger log=LoggerFactory.getLogger(MolitImportRunner.class);
 private final MolitStore store;
 private final String key,month;
 private final boolean demo;
 public MolitImportRunner(MolitStore store,@Value("${MOLIT_SERVICE_KEY:}") String key,
   @Value("${molit.month:202609}") String month,@Value("${app.demo:false}") boolean demo) {
  this.store=store;this.key=key;this.month=month;this.demo=demo;
 }
 @Override public void run(String...args) {
  if(demo)throw new IllegalArgumentException("수집은 molit 프로필 또는 샘플 데이터가 없는 postgres 프로필에서 실행하세요.");
  YearMonth ym;
  try {if(!month.matches("[0-9]{6}"))throw new IllegalArgumentException();ym=YearMonth.parse(month,DateTimeFormatter.ofPattern("uuuuMM"));}
  catch(Exception e){throw new IllegalArgumentException("molit.month는 YYYYMM 형식이어야 합니다.");}
  log.info("MOLIT 수집 시작: 종로구(11110), 계약월 {}",month);
  var items=MolitClient.live(key).fetch(ym);
  int count=store.replace(ym,items);
  log.info("MOLIT 수집 완료: 종로구(11110), 계약월 {}, 유효 거래 {}건, 해제 거래 {}건 제외",month,count,items.size()-count);
 }
}
