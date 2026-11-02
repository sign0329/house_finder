package kr.aptfolio.collection.jobs;
import kr.aptfolio.collection.molit.MolitClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
@Configuration
@ConditionalOnProperty(name="collection.api.enabled",havingValue="true")
class CollectionApiConfiguration {
 @Bean MolitClient collectionApiClient(@Value("${MOLIT_SERVICE_KEY:}") String key){return MolitClient.live(key);}
}
