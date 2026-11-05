package kr.aptfolio.bookmark;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Bookmarks {
 private final NamedParameterJdbcTemplate db;
 public Bookmarks(NamedParameterJdbcTemplate db){this.db=db;}
 public record Item(long id,String key,Long tradeId,String name,String location){}
 private record Apartment(String region,String district,String name,String address) {
  String key(){return region+"|"+district+"|"+name+"|"+address;}
  String hash(){
   String identity="";for(String value:List.of(region,district,name,address))identity+=value.length()+":"+value;
   try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8)));}
   catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
  }
 }
 private long userId(String username,boolean lock){
  return db.query("select id from user_account where username=:username"+(lock?" for update":""),Map.of("username",username),(r,n)->r.getLong(1))
   .stream().findFirst().orElseThrow(NoSuchElementException::new);
 }
 @Transactional public long save(String username,long tradeId){
  if(tradeId<=0)throw new IllegalArgumentException();
  long userId=userId(username,true);
  var apt=db.query("select region,district,apartment,address from trade where id=:id",Map.of("id",tradeId),
   (r,n)->new Apartment(r.getString("region"),r.getString("district"),r.getString("apartment"),r.getString("address")))
   .stream().findFirst().orElseThrow(NoSuchElementException::new);
  var params=new HashMap<String,Object>();params.put("user",userId);params.put("key",apt.hash());
  var existing=db.query("select id from user_bookmark where user_id=:user and apartment_key=:key",params,(r,n)->r.getLong(1));
  if(!existing.isEmpty())return existing.getFirst();
  if(db.queryForObject("select count(*) from user_bookmark where user_id=:user",params,Integer.class)>=200)throw new IllegalStateException("최대 200개까지 저장할 수 있습니다.");
  params.put("region",apt.region());params.put("district",apt.district());params.put("name",apt.name());params.put("address",apt.address());
  db.update("insert into user_bookmark(user_id,apartment_key,region,district,apartment,address) values(:user,:key,:region,:district,:name,:address)",params);
  return db.queryForObject("select id from user_bookmark where user_id=:user and apartment_key=:key",params,Long.class);
 }
 @Transactional(readOnly=true) public List<Item> list(String username){
  return db.query("""
   select b.*, (select t.id from trade t where t.region=b.region and t.district=b.district
    and t.apartment=b.apartment and t.address=b.address order by t.deal_date desc,t.id desc limit 1) as current_trade_id
   from user_bookmark b where b.user_id=:user order by b.created_at desc,b.id desc limit 200
   """,Map.of("user",userId(username,false)),(r,n)->new Item(r.getLong("id"),
    new Apartment(r.getString("region"),r.getString("district"),r.getString("apartment"),r.getString("address")).key(),
    r.getObject("current_trade_id",Long.class),r.getString("apartment"),r.getString("region")+" "+r.getString("district")));
 }
 @Transactional public void remove(String username,long id){
  long userId=userId(username,true);
  if(db.update("delete from user_bookmark where user_id=:user and id=:id",Map.of("user",userId,"id",id))==0)throw new NoSuchElementException();
 }
}
