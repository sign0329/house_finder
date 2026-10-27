package kr.aptfolio.molit;

import java.net.URI;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class MolitClient {
 private static final String ENDPOINT="https://apis.data.go.kr/1613000/RTMSDataSvcAptTrade/getRTMSDataSvcAptTrade";
 public record Reply(int status,String body) {}
 @FunctionalInterface public interface Transport {Reply get(URI uri) throws Exception;}
 private final String key;
 private final Transport transport;
 public MolitClient(String key,Transport transport) {this.key=key;this.transport=transport;}
 public static MolitClient live(String key) {
  var http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
  return new MolitClient(key,uri->{
   var req=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).header("Accept","application/xml").GET().build();
   var response=http.send(req,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
   return new Reply(response.statusCode(),response.body());
  });
 }
 public List<MolitXml.Item> fetch(YearMonth month) { return fetch(MolitRegion.JONGNO, month); }
 public List<MolitXml.Item> fetch(MolitRegion region, YearMonth month) {
  if(key==null||key.isBlank())throw new IllegalArgumentException("MOLIT_SERVICE_KEY 환경 변수를 설정하세요.");
  if(month.isAfter(YearMonth.now())||month.isBefore(YearMonth.of(2006,1)))throw new IllegalArgumentException("수집 계약월을 확인하세요.");
  String encodedKey=encodeServiceKey(key);
  List<MolitXml.Item> items=new ArrayList<>();int total=-1;int size=-1;
  for(int page=1;page<=100;page++) {
   URI uri=URI.create(ENDPOINT+"?serviceKey="+encodedKey+"&LAWD_CD="+region.code()+"&DEAL_YMD="+month.format(DateTimeFormatter.ofPattern("yyyyMM"))+"&pageNo="+page+"&numOfRows=100");
   Reply reply=request(uri);
   var parsed=new MolitXml().parse(reply.body(),region,month);
   if(total<0){total=parsed.total();size=parsed.size();}
   int expected=Math.min(size,total-items.size());
   if(parsed.number()!=page||parsed.total()!=total||parsed.size()!=size||parsed.items().size()!=expected)
    throw new IllegalArgumentException("수집 중 페이지 또는 건수가 바뀌었습니다. 기존 데이터는 유지됩니다. 다시 실행하세요.");
   items.addAll(parsed.items());
   if(items.size()==total)return List.copyOf(items);
  }
  throw new IllegalArgumentException("수집 페이지 상한을 초과했습니다. 기존 데이터는 유지됩니다.");
 }
 private static String encodeServiceKey(String input) {
  String value=input.trim();
  // Portal Encoding keys contain percent escapes. Decode once, preserving literal +.
  // URLDecoder normally treats + as space; that would corrupt raw service keys.
  if(value.contains("%")) {
   try {value=URLDecoder.decode(value.replace("+","%2B"),StandardCharsets.UTF_8);}
   catch(IllegalArgumentException e) {
    throw new IllegalArgumentException("인증키의 URL 인코딩 형식이 잘못되었습니다. 포털의 Encoding 또는 Decoding 키를 다시 복사하세요.");
   }
  }
  return URLEncoder.encode(value,StandardCharsets.UTF_8);
 }
 private Reply request(URI uri) {
  for(int attempt=0;attempt<3;attempt++) {
   try {
    Reply reply=transport.get(uri);
    if(reply.status()==200)return reply;
    if(reply.status()!=429&&reply.status()<500)break;
   } catch(InterruptedException e){Thread.currentThread().interrupt();break;}
   catch(Exception ignored) { /* sanitized below */ }
   if(attempt<2)try{Thread.sleep(500L*(attempt+1));}catch(InterruptedException e){Thread.currentThread().interrupt();break;}
  }
  throw new IllegalArgumentException("국토부 API 호출 실패: 인증·호출 한도·네트워크를 확인한 뒤 다시 실행하세요. 기존 데이터는 유지됩니다.");
 }
}
