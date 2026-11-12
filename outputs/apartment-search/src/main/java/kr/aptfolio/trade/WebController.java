package kr.aptfolio.trade;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
@Controller
public class WebController {
 private final SearchService service;
 private final KakaoMapSettings maps;
 private final TradeTrends trends;
 public WebController(SearchService service,KakaoMapSettings maps,TradeTrends trends){this.service=service;this.maps=maps;this.trends=trends;}
 @ModelAttribute void shared(Model m){m.addAttribute("dataNotice",service.dataNotice());m.addAttribute("regions",Provinces.all());}
 @GetMapping("/") String home(@RequestParam Map<String,String> params,Model m){
  var result=service.search(params);m.addAttribute("result",result);m.addAttribute("filters",params);m.addAttribute("selectedRegion",Provinces.canonical(params.getOrDefault("region","")));
  m.addAttribute("previous",pageUrl(params,Math.max(0,result.page()-1)));
  m.addAttribute("next",pageUrl(params,result.page()+1));return "index";
 }
 private String pageUrl(Map<String,String> params,int page){var b=UriComponentsBuilder.fromPath("/");params.forEach((k,v)->{if(!k.equals("page"))b.queryParam(k,v);});return b.queryParam("page",page).build().encode().toUriString();}
 @GetMapping("/api/trades") @ResponseBody SearchService.Results api(@RequestParam Map<String,String> params){return service.search(params);}
 @GetMapping("/trades/{id}") String detail(@PathVariable long id,@RequestParam(defaultValue="12") int months,Model m){
  var t=service.find(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
  m.addAttribute("mapKey",maps.key());
  m.addAttribute("mapSearchUrl","https://map.kakao.com/link/search/"+org.springframework.web.util.UriUtils.encodePathSegment(t.address(),java.nio.charset.StandardCharsets.UTF_8));
  m.addAttribute("trend",trends.monthly(t,months));
  var history=service.history(t);m.addAttribute("trade",t);m.addAttribute("history",history);
  return "detail";
 }
 @GetMapping("/saved") String saved(){return "saved";}
 @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
 String invalid(IllegalArgumentException e,Model m){m.addAttribute("message",e.getMessage());return "error/400";}
}
