package kr.aptfolio.molit;

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
 private final String key,month,regions,from,to;
 private final boolean demo;
 public MolitImportRunner(MolitStore store,@Value("${MOLIT_SERVICE_KEY:}") String key,
   @Value("${molit.month:202609}") String month,@Value("${app.demo:false}") boolean demo,
   @Value("${molit.regions:11110:서울:종로구}") String regions,
   @Value("${molit.from:}") String from,@Value("${molit.to:}") String to) {
  this.store=store;this.key=key;this.month=month;this.demo=demo;
  this.regions=regions;this.from=from;this.to=to;
 }
 @Override public void run(String...args) {
  if(demo)throw new IllegalArgumentException("샘플 데이터가 없는 DB에서 수집하세요.");
  var plan=MolitPlan.parse(regions,month,from,to);
  if(key.isBlank())throw new IllegalArgumentException("MOLIT_SERVICE_KEY 환경 변수를 설정하세요.");
  log.info("MOLIT 수집 시작: 지역 {}개, 월 {}개",plan.regions().size(),plan.months().size());
  var results=new MolitCollector(MolitClient.live(key),store).collect(plan,result->{
   if(result.success())log.info("MOLIT 성공: 지역 {}, 계약월 {}, 저장 {}건, 해제 제외 {}건",result.regionCode(),result.month(),result.saved(),result.cancelled());
   else log.error("MOLIT 실패: 지역 {}, 계약월 {}. 해당 구간 기존 데이터 유지; 인증·한도·네트워크·응답을 확인하세요.",result.regionCode(),result.month());
  });
  long failed=results.stream().filter(r->!r.success()).count();
  log.info("MOLIT 수집 결과: 성공 {}구간, 실패 {}구간",results.size()-failed,failed);
  if(failed>0)throw new IllegalStateException("일부 수집 구간이 실패했습니다. 로그의 지역·월을 지정해 재실행하세요. 성공 구간은 저장되었습니다.");
 }
}
