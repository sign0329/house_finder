package kr.aptfolio;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
@Service
public class SearchService {
 private final NamedParameterJdbcTemplate db;
 private static final RowMapper<Trade> ROW=(r,n)->new Trade(r.getLong("id"),r.getString("region"),r.getString("district"),r.getString("apartment"),r.getString("address"),r.getBigDecimal("area"),r.getInt("floor"),r.getInt("built_year"),r.getDate("deal_date").toLocalDate(),r.getLong("price"));
 public SearchService(NamedParameterJdbcTemplate db) {this.db=db;}
 public List<String> regions() { return db.queryForList("select distinct region from trade order by region",Map.of(),String.class); }
 public record Results(List<Trade> items,long total,int page,int pages) {}
 public Results search(Map<String,String> input) {
  Map<String,Object> args=new HashMap<>(); StringBuilder where=new StringBuilder(" where 1=1");
  String q=input.getOrDefault("q","").trim();
  if(q.length()>100) throw new IllegalArgumentException("검색어는 100자 이내로 입력해주세요.");
  if(!q.isEmpty()) {where.append(" and (apartment like :q escape '!' or district like :q escape '!')");args.put("q","%"+q.replace("!","!!").replace("%","!%").replace("_","!_")+"%");}
  String region=input.getOrDefault("region","");
  if(!region.isEmpty()) {where.append(" and region in (:region)");args.put("region",Provinces.aliases(region));}
  Long min=number(input,"minPrice"), max=number(input,"maxPrice");
  if(min!=null && max!=null && min>max) throw new IllegalArgumentException("최소 가격이 최대 가격보다 큽니다.");
  if(min!=null){where.append(" and price>=:min");args.put("min",min);}
  if(max!=null){where.append(" and price<=:max");args.put("max",max);}
  LocalDate from=date(input,"from"),to=date(input,"to");
  if(from!=null&&to!=null&&from.isAfter(to))throw new IllegalArgumentException("시작일이 종료일보다 늦습니다.");
  if(from!=null){where.append(" and deal_date>=:from");args.put("from",from);}
  if(to!=null){where.append(" and deal_date<=:to");args.put("to",to);}
  String area=input.getOrDefault("area","");
  switch(area){case "small"->where.append(" and area<60");case "medium"->where.append(" and area>=60 and area<=85");case "large"->where.append(" and area>85");case ""->{} default->throw new IllegalArgumentException("면적 조건을 확인해주세요.");}
  int page;
  try{page=Integer.parseInt(input.getOrDefault("page","0"));}catch(NumberFormatException e){throw new IllegalArgumentException("페이지 번호를 확인해주세요.");}
  if(page<0||page>10000)throw new IllegalArgumentException("페이지 범위를 벗어났습니다.");
  String order=switch(input.getOrDefault("sort","recent")){case "priceAsc"->"price asc, id desc";case "priceDesc"->"price desc, id desc";case "recent"->"deal_date desc, id desc";default->throw new IllegalArgumentException("정렬 조건을 확인해주세요.");};
  long total=db.queryForObject("select count(*) from trade"+where,args,Long.class);
  args.put("limit",12);args.put("offset",page*12);
  return new Results(db.query("select * from trade"+where+" order by "+order+" limit :limit offset :offset",args,ROW),total,page,(int)((total+11)/12));
 }
 private Long number(Map<String,String> in,String key){
  if(in.getOrDefault(key,"").isBlank())return null;
  try{long n=Long.parseLong(in.get(key));if(n<0)throw new NumberFormatException();return n;}
  catch(NumberFormatException e){throw new IllegalArgumentException("가격은 0 이상의 정수(만원)로 입력해주세요.");}
 }
 private LocalDate date(Map<String,String> in,String key){
  if(in.getOrDefault(key,"").isBlank())return null;
  try{return LocalDate.parse(in.get(key));}catch(Exception e){throw new IllegalArgumentException("날짜 형식을 확인해주세요.");}
 }
 public String dataNotice() {
  var rows=db.queryForList("select last_month, updated_at from molit_import_state where id=1",Map.of());
  if(rows.isEmpty()||rows.getFirst().get("updated_at")==null)return "아직 수집된 공공데이터가 없습니다.";
  var row=rows.getFirst();
  return "출처: 국토교통부 · 종로구 · 최근 수집 계약월 "+row.get("last_month")+" · 수집 시각 "+row.get("updated_at")+" (서버 시간) · 신고·정정에 따라 변경될 수 있습니다.";
 }
 public Optional<Trade> find(long id){return db.query("select * from trade where id=:id",Map.of("id",id),ROW).stream().findFirst();}
 public List<Trade> history(Trade t){return db.query("select * from trade where region=:r and district=:d and apartment=:a and address=:s and area=:area order by deal_date desc,id desc limit 12",Map.of("r",t.region(),"d",t.district(),"a",t.apartment(),"s",t.address(),"area",t.area()),ROW);}
}
