package au.org.ala.listsapi.service;

import au.org.ala.listsapi.service.auth.WebService;
import org.apache.http.entity.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UserdetailsService {
    @Autowired WebService webService;

    @Value("${userDetails.api.url}")
    private String userdetailsUrl;

    private static final Logger logger = LoggerFactory.getLogger(UserdetailsService.class);

    public Map fetchUserByEmail(String email) {
        logger.info("Userdetails fetch {} ({})", userdetailsUrl + "/userDetails/getUserDetails", email);

        Map params = Map.of("userName", email);
        Map request = webService.post(
                userdetailsUrl + "/userDetails/getUserDetails",
                null,
                params,
                ContentType.APPLICATION_JSON,
                true,
                false,
                null
        );

        if ((int)request.get("statusCode") == 200) {
            Map resp = (Map)request.get("resp");
            logger.info("Userdetails fetch succeeded - {} - {}", resp.get("displayName"), resp.get("userId"));

            return resp;
        } else {
            logger.info("Userdetails fetch failed ({})", request.get("statusCode"));
        }

        return null;
    }
}
