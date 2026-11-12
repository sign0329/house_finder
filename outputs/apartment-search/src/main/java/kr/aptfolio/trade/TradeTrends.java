package kr.aptfolio.trade;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TradeTrends {
 private final NamedParameterJdbcTemplate db;
 public TradeTrends(NamedParameterJdbcTemplate db){this.db=db;}
 public record Month(YearMonth month,long count,BigDecimal averagePrice,Long minPrice,Long maxPrice) {
  public String averageLabel(){return averagePrice==null ? "거래 없음" : String.format(Locale.KOREA,"%,.0f만원",averagePrice);}
  public String minLabel(){return minPrice==null ? "—" : String.format(Locale.KOREA,"%,d만원",minPrice);}
  public String maxLabel(){return maxPrice==null ? "—" : String.format(Locale.KOREA,"%,d만원",maxPrice);}
 }
 public record Trend(List<Month> points,int months,YearMonth from,YearMonth to,long total,BigDecimal priceMax,long volumeMax) {
  public int priceHeight(Month point){return point.averagePrice()==null ? 0 : point.averagePrice().multiply(BigDecimal.valueOf(160)).divide(priceMax,0,RoundingMode.HALF_UP).intValue();}
  public int volumeHeight(Month point){return (int)Math.round(point.count()*100.0/volumeMax);}
 }
 @Transactional(readOnly=true)
 public Trend monthly(Trade trade,int months){
  if(months!=6 && months!=12 && months!=36)throw new IllegalArgumentException("조회 기간은 6개월, 1년, 3년 중 선택해주세요.");
  Map<String,Object> args=new HashMap<>(Map.of("r",trade.region(),"d",trade.district(),"a",trade.apartment(),"s",trade.address(),"area",trade.area()));
  String identity="region=:r and district=:d and apartment=:a and address=:s and area=:area";
  var latest=db.queryForObject("select max(deal_date) from trade where "+identity,args,java.sql.Date.class);
  YearMonth to=YearMonth.from(latest==null?trade.dealDate():latest.toLocalDate()),from=to.minusMonths(months-1);
  args.put("from",from.atDay(1));args.put("until",to.plusMonths(1).atDay(1));
  var rows=db.query("select extract(year from deal_date) as y, extract(month from deal_date) as m, count(*) as n, avg(price) as average_price, min(price) as low_price, max(price) as high_price from trade where "+identity+" and deal_date>=:from and deal_date<:until group by extract(year from deal_date),extract(month from deal_date) order by y,m",args,
   (r,n)->new Month(YearMonth.of(r.getInt("y"),r.getInt("m")),r.getLong("n"),r.getBigDecimal("average_price").setScale(2,RoundingMode.HALF_UP),r.getLong("low_price"),r.getLong("high_price")));
  Map<YearMonth,Month> found=new HashMap<>();rows.forEach(row->found.put(row.month(),row));
  List<Month> points=new ArrayList<>();
  for(int i=0;i<months;i++){var month=from.plusMonths(i);points.add(found.getOrDefault(month,new Month(month,0,null,null,null)));}
  return new Trend(List.copyOf(points),months,from,to,rows.stream().mapToLong(Month::count).sum(),rows.stream().map(Month::averagePrice).max(BigDecimal::compareTo).orElse(BigDecimal.ONE),Math.max(1,rows.stream().mapToLong(Month::count).max().orElse(0)));
 }
}
