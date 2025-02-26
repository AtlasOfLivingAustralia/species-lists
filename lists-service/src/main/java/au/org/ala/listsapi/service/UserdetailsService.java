package au.org.ala.listsapi.service;

import au.org.ala.listsapi.service.auth.WebService;
import org.apache.http.entity.ContentType;
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

    public Map fetchUserByEmail(String email) {
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
            return (Map)request.get("resp");
        }

        return null;
    }
}
