/*
 * Copyright (C) 2025 Atlas of Living Australia
 * All Rights Reserved.
 *
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 */
package au.org.ala.listsapi.service;

import au.org.ala.listsapi.service.auth.WebService;
import java.util.Map;
import org.apache.http.entity.ContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class UserdetailsService {
  @Autowired WebService webService;

  @Value("${userDetails.api.url}")
  private String userdetailsUrl;

  public Map fetchUserByEmail(String email) {
    Map<String, Object> params = Map.of("userName", email);
    Map request =
        webService.post(
            userdetailsUrl + "/userDetails/getUserDetails",
            null,
            params,
            ContentType.APPLICATION_JSON,
            true,
            false,
            null);

    if ((int) request.get("statusCode") == 200) {
      Map resp = (Map) request.get("resp");

      return resp;
    }

    return null;
  }
}
