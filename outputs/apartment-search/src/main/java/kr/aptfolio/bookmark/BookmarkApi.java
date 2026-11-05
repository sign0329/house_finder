package kr.aptfolio.bookmark;

import java.security.Principal;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/account/bookmarks")
public class BookmarkApi {
 private final Bookmarks bookmarks;
 public BookmarkApi(Bookmarks bookmarks){this.bookmarks=bookmarks;}
 public record Request(long tradeId){}
 @GetMapping public Map<String,Object> list(Principal principal){return Map.of("username",principal.getName(),"items",bookmarks.list(principal.getName()));}
 @PostMapping public Map<String,Long> save(Principal principal,@RequestBody Request request){return Map.of("id",bookmarks.save(principal.getName(),request.tradeId()));}
 @DeleteMapping("/{id}") public ResponseEntity<Void> remove(Principal principal,@PathVariable long id){bookmarks.remove(principal.getName(),id);return ResponseEntity.noContent().build();}
 @ExceptionHandler(NoSuchElementException.class) ResponseEntity<Map<String,String>> missing(){return ResponseEntity.status(404).body(Map.of("error","not_found"));}
 @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<Map<String,String>> invalid(){return ResponseEntity.badRequest().body(Map.of("error","invalid_request"));}
 @ExceptionHandler(IllegalStateException.class) ResponseEntity<Map<String,String>> limit(){return ResponseEntity.status(409).body(Map.of("error","bookmark_limit"));}
}
