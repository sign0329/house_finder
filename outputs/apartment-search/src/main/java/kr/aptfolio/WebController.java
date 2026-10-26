package kr.aptfolio;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
@Controller
public class WebController {
 private final SearchService service;
 @Value("${app.demo:false}") boolean demo;
 public WebController(SearchService service){this.service=service;}
 @ModelAttribute void shared(Model m){m.addAttribute("demo",demo);m.addAttribute("regions",List.of("서울","경기","부산","대전","대구"));}
 @GetMapping("/") String home(@RequestParam Map<String,String> params,Model m){
  var result=service.search(params);m.addAttribute("result",result);m.addAttribute("filters",params);
  m.addAttribute("previous",pageUrl(params,Math.max(0,result.page()-1)));
  m.addAttribute("next",pageUrl(params,result.page()+1));return "index";
 }
 private String pageUrl(Map<String,String> params,int page){var b=UriComponentsBuilder.fromPath("/");params.forEach((k,v)->{if(!k.equals("page"))b.queryParam(k,v);});return b.queryParam("page",page).build().encode().toUriString();}
 @GetMapping("/api/trades") @ResponseBody SearchService.Results api(@RequestParam Map<String,String> params){return service.search(params);}
 @GetMapping("/trades/{id}") String detail(@PathVariable long id,Model m){
  var t=service.find(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
  var history=service.history(t);m.addAttribute("trade",t);m.addAttribute("history",history);
  m.addAttribute("chartMax",history.stream().mapToLong(Trade::price).max().orElse(1));return "detail";
 }
 @GetMapping("/saved") String saved(){return "saved";}
 @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
 String invalid(IllegalArgumentException e,Model m){m.addAttribute("message",e.getMessage());return "error/400";}
}
