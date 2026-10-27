package kr.aptfolio.molit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MolitStore {
 private final JdbcTemplate db;
 public MolitStore(JdbcTemplate db) {this.db=db;}
 @Transactional
 public int replace(YearMonth month,List<MolitXml.Item> items) {
  return replace(MolitRegion.JONGNO,month,items);
 }
 @Transactional
 public int replace(MolitRegion region,YearMonth month,List<MolitXml.Item> items) {
  db.queryForObject("select id from molit_import_state where id=1 for update",Integer.class);
  if(db.queryForObject("select count(*) from trade where source <> 'MOLIT'",Long.class)>0)
   throw new IllegalArgumentException("가상 데이터가 있는 DB에는 수집할 수 없습니다. molit 프로필의 별도 DB를 사용하세요.");
  String ym=month.format(DateTimeFormatter.ofPattern("yyyyMM"));String snapshot=UUID.randomUUID().toString();
  var occurrences=new HashMap<String,Integer>();int count=0;
  for(var item:items) {
   if(!YearMonth.from(item.date()).equals(month))throw new IllegalArgumentException("계약월 불일치");
   if(!item.address().startsWith(region.addressPrefix()))throw new IllegalArgumentException("수집 지역과 주소 불일치");
   if(item.cancelled())continue;
   String hash=hash(item);String key=(region.equals(MolitRegion.JONGNO)?"":region.code()+"-")+hash+"-"+occurrences.merge(hash,1,Integer::sum);
   int updated=db.update("update trade set snapshot_id=? where source_key=? and source_region=?",snapshot,key,region.code());
   if(updated==0)db.update("insert into trade(region,district,apartment,address,area,floor,built_year,deal_date,price,source,source_key,source_month,snapshot_id,source_region) values(?,?,?,?,?,?,?,?,?,'MOLIT',?,?,?,?)",
    region.province(),region.district(),item.apartment(),item.address(),item.area(),item.floor(),item.builtYear(),item.date(),item.price(),key,ym,snapshot,region.code());
   count++;
  }
  db.update("delete from trade where source='MOLIT' and source_region=? and source_month=? and snapshot_id<>?",region.code(),ym,snapshot);
  db.update("update molit_import_state set last_month=?,last_count=?,updated_at=CURRENT_TIMESTAMP where id=1",ym,count);
  return count;
 }
 private static String hash(MolitXml.Item item) {
  try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(item.toString().getBytes(StandardCharsets.UTF_8)));}
  catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException("SHA-256 unavailable");}
 }
}
