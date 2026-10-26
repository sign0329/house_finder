package kr.aptfolio;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.Random;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
@Component @ConditionalOnProperty(name="app.demo",havingValue="true")
public class DemoData implements CommandLineRunner {
 private final JdbcTemplate db;
 public DemoData(JdbcTemplate db) {this.db=db;}
 @Override @Transactional public void run(String...args) {
  if(db.queryForObject("select count(*) from trade",Long.class)>0) return;
  String[][] homes={{"서울","마포구","한강 포레스트","마포구 샘플로 12"},
   {"서울","성동구","서울숲 리버뷰","성동구 샘플길 24"},
   {"서울","강동구","고덕 그린파크","강동구 샘플로 35"},
   {"경기","수원시","광교 레이크힐","수원시 샘플길 42"},
   {"경기","성남시","판교 센트럴","성남시 샘플로 8"},
   {"부산","해운대구","해운대 오션시티","해운대구 샘플길 17"},
   {"대전","유성구","유성 온빛마을","유성구 샘플로 21"},
   {"대구","수성구","수성 더테라스","수성구 샘플길 33"}};
  Random r=new Random(42);
  for(int i=0;i<240;i++) {
   String[] h=homes[i%homes.length];
   db.update("insert into trade(region,district,apartment,address,area,floor,built_year,deal_date,price) values(?,?,?,?,?,?,?,?,?)",
    h[0],h[1],h[2],h[3],new BigDecimal(new String[]{"59.98","84.95","114.72"}[i%3]),
    1+r.nextInt(28),2010+i%13,LocalDate.of(2026,10,12).minusDays(i*2L),
    35000L+(7-i%8)*10500L+r.nextInt(200)*100L);
  }
 }
}
