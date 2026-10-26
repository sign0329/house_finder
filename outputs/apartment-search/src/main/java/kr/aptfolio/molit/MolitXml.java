package kr.aptfolio.molit;

import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

/** Official basic apartment trade API. Fail closed rather than publishing an incomplete snapshot. */
public class MolitXml {
 public record Item(String apartment,String address,BigDecimal area,int floor,int builtYear,LocalDate date,long price,boolean cancelled) {}
 public record Page(int total,int number,int size,List<Item> items) {}
 public Page parse(String xml, YearMonth month) {
  try {
   if(xml.length()>2_000_000)throw new IllegalArgumentException();
   var factory=DocumentBuilderFactory.newInstance();
   factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
   factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
   factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");
   factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
   factory.setXIncludeAware(false);factory.setExpandEntityReferences(false);
   var builder=factory.newDocumentBuilder();builder.setErrorHandler(new DefaultHandler());
   var root=builder.parse(new InputSource(new StringReader(xml))).getDocumentElement();
   String code=text(root,"resultCode");
   if(!code.matches("0{1,3}"))throw new IllegalArgumentException();
   int total=Integer.parseInt(text(root,"totalCount"));
   int number=Integer.parseInt(text(root,"pageNo"));
   int size=Integer.parseInt(text(root,"numOfRows"));
   if(total<0||total>10000||number<1||size<1)throw new IllegalArgumentException();
   List<Item> items=new ArrayList<>();var nodes=root.getElementsByTagName("item");
   for(int i=0;i<nodes.getLength();i++) {
    var e=(Element)nodes.item(i);
    if(!text(e,"sggCd").equals("11110"))throw new IllegalArgumentException();
    LocalDate date=LocalDate.of(Integer.parseInt(text(e,"dealYear")),Integer.parseInt(text(e,"dealMonth")),Integer.parseInt(text(e,"dealDay")));
    if(!YearMonth.from(date).equals(month))throw new IllegalArgumentException();
    String apartment=required(e,"aptNm");
    String address="서울 종로구 "+required(e,"umdNm")+" "+text(e,"jibun");
    BigDecimal area=new BigDecimal(required(e,"excluUseAr")).setScale(4,java.math.RoundingMode.UNNECESSARY);
    long price=Long.parseLong(required(e,"dealAmount").replace(",",""));
    int floor=Integer.parseInt(required(e,"floor"));
    int built=Integer.parseInt(required(e,"buildYear"));
    if(price<=0||area.signum()<=0||area.precision()>12||apartment.length()>120||address.length()>200)throw new IllegalArgumentException();
    String cancelled=text(e,"cdealType");
    if(!cancelled.isEmpty()&&!cancelled.equals("O"))throw new IllegalArgumentException();
    items.add(new Item(apartment,address.trim(),area,floor,built,date,price,cancelled.equals("O")||!text(e,"cdealDay").isEmpty()));
   }
   return new Page(total,number,size,List.copyOf(items));
  } catch(Exception e) {
   // Neither raw XML nor parser/HTTP causes may leak an upstream echoed credential.
   throw new IllegalArgumentException("국토부 응답을 검증하지 못했습니다. API 승인·키·데이터 필드를 확인하세요. 기존 데이터는 유지됩니다.");
  }
 }
 private static String required(Element e,String name) {String value=text(e,name);if(value.isBlank())throw new IllegalArgumentException();return value;}
 private static String text(Element e,String name) {var nodes=e.getElementsByTagName(name);return nodes.getLength()==0?"":nodes.item(0).getTextContent().trim();}
}
