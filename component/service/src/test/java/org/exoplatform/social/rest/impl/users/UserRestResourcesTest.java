package org.exoplatform.social.rest.impl.users;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStream;
import java.net.URL;
import java.util.*;

import javax.ws.rs.core.MultivaluedMap;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

import org.exoplatform.commons.utils.IOUtil;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.services.rest.impl.ContainerResponse;
import org.exoplatform.services.rest.impl.MultivaluedMapImpl;
import org.exoplatform.services.user.UserStateModel;
import org.exoplatform.services.user.UserStateService;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.social.core.activity.model.ExoSocialActivity;
import org.exoplatform.social.core.activity.model.ExoSocialActivityImpl;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.*;
import org.exoplatform.social.core.service.LinkProvider;
import org.exoplatform.social.core.space.impl.DefaultSpaceApplicationHandler;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.mock.MockUploadService;
import org.exoplatform.social.rest.api.ErrorResource;
import org.exoplatform.social.rest.api.UserImportResultEntity;
import org.exoplatform.social.rest.entity.*;
import org.exoplatform.social.rest.impl.user.UserRestResourcesV1;
import org.exoplatform.social.service.test.AbstractResourceTest;
import org.exoplatform.upload.UploadResource;
import org.exoplatform.upload.UploadService;

public class UserRestResourcesTest extends AbstractResourceTest {

  private ActivityManager     activityManager;

  private IdentityManager     identityManager;

  private UserACL             userACL;

  private RelationshipManager relationshipManager;

  private SpaceService        spaceService;

  private OrganizationService organizationService;

  private UserStateService    userStateService;

  private MockUploadService   uploadService;

  private Identity            rootIdentity;

  private Identity            johnIdentity;

  private Identity            maryIdentity;

  private Identity            demoIdentity;

  public void setUp() throws Exception {
    super.setUp();

    System.setProperty("gatein.email.domain.url", "localhost:8080");

    activityManager = getContainer().getComponentInstanceOfType(ActivityManager.class);
    identityManager = getContainer().getComponentInstanceOfType(IdentityManager.class);
    userACL = getContainer().getComponentInstanceOfType(UserACL.class);
    relationshipManager = getContainer().getComponentInstanceOfType(RelationshipManager.class);
    spaceService = getContainer().getComponentInstanceOfType(SpaceService.class);
    userStateService = getContainer().getComponentInstanceOfType(UserStateService.class);
    uploadService = (MockUploadService) getContainer().getComponentInstanceOfType(UploadService.class);
    organizationService = getContainer().getComponentInstanceOfType(OrganizationService.class);
    rootIdentity = new Identity(OrganizationIdentityProvider.NAME, "root");
    johnIdentity = new Identity(OrganizationIdentityProvider.NAME, "john");
    maryIdentity = new Identity(OrganizationIdentityProvider.NAME, "mary");
    demoIdentity = new Identity(OrganizationIdentityProvider.NAME, "demo");

    identityManager.saveIdentity(rootIdentity);
    identityManager.saveIdentity(johnIdentity);
    identityManager.saveIdentity(maryIdentity);
    identityManager.saveIdentity(demoIdentity);

    addResource(UserRestResourcesV1.class, null);
  }

  public void tearDown() throws Exception {
    super.tearDown();
    removeResource(UserRestResourcesV1.class);
  }

  public void testGetAllUsers() throws Exception {
    startSessionAs("root");
    ContainerResponse response = service("GET", getURLResource("users?limit=5&offset=0"), "", null, null);
    assertNotNull(response);
    assertEquals(200, response.getStatus());
    CollectionEntity collections = (CollectionEntity) response.getEntity();
    assertEquals(4, collections.getEntities().size());
  }

  public void testGetAllUsersWithExtraFields() throws Exception {
    Space spaceTest = getSpaceInstance(700, "root");
    spaceTest.getId();
    spaceTest.setMembers(new String[] { "mary", "john", "demo" });
    spaceService.updateSpace(spaceTest);

    relationshipManager.inviteToConnect(rootIdentity, maryIdentity);

    relationshipManager.inviteToConnect(demoIdentity, rootIdentity);

    relationshipManager.inviteToConnect(rootIdentity, johnIdentity);
    relationshipManager.confirm(johnIdentity, rootIdentity);

    relationshipManager.inviteToConnect(maryIdentity, johnIdentity);
    relationshipManager.confirm(johnIdentity, maryIdentity);

    relationshipManager.inviteToConnect(demoIdentity, johnIdentity);
    relationshipManager.confirm(johnIdentity, demoIdentity);

    startSessionAs("root");
    ContainerResponse response =
                               service("GET",
                                       getURLResource("users?limit=5&offset=0&expand=all,connectionsCount,spacesCount,connectionsInCommonCount,relationshipStatus"),
                                       "",
                                       null,
                                       null);
    assertNotNull(response);
    assertEquals(200, response.getStatus());
    CollectionEntity collections = (CollectionEntity) response.getEntity();
    assertEquals(4, collections.getEntities().size());

    List<? extends DataEntity> entities = collections.getEntities();
    for (DataEntity dataEntity : entities) {
      String username = dataEntity.get("username").toString();
      if (StringUtils.equals(username, "root")) {
        continue;
      }
      String connectionsCount = dataEntity.get("connectionsCount").toString();
      String spacesCount = dataEntity.get("spacesCount").toString();
      String connectionsInCommonCount = dataEntity.get("connectionsInCommonCount").toString();
      String relationshipStatus = dataEntity.get("relationshipStatus").toString();
      if (StringUtils.equals(username, "john")) {
        assertEquals("3", connectionsCount);
      } else {
        assertEquals("1", connectionsCount);
      }
      assertEquals("1", spacesCount);
      if (StringUtils.equals(username, "john")) {
        assertEquals("CONFIRMED", relationshipStatus);
      } else if (StringUtils.equals(username, "mary")) {
        assertEquals("OUTGOING", relationshipStatus);
      } else if (StringUtils.equals(username, "demo")) {
        assertEquals("INCOMING", relationshipStatus);
      }
      if (StringUtils.equals(username, "john")) {
        assertEquals("0", connectionsInCommonCount);
      } else {
        assertEquals("1", connectionsInCommonCount);
      }
    }
  }

  public void testGetOnlineUsers() throws Exception {
    startSessionAs("root");
    long date = new Date().getTime();
    UserStateModel userModel =
                             new UserStateModel("john",
                                                date,
                                                UserStateService.DEFAULT_STATUS);
    UserStateModel userModel2 =
                              new UserStateModel("mary",
                                                 date,
                                                 UserStateService.DEFAULT_STATUS);
    userStateService.save(userModel);
    userStateService.save(userModel2);
    userStateService.ping(userModel.getUserId());
    userStateService.ping(userModel2.getUserId());
    ContainerResponse response = service("GET", getURLResource("users?status=online&limit=5&offset=0"), "", null, null);
    assertNotNull(response);
    assertEquals(200, response.getStatus());
    CollectionEntity collections = (CollectionEntity) response.getEntity();
    assertEquals(2, collections.getEntities().size()); // john and mary
  }

  public void testGetOnlineUsersOfSpace() throws Exception {
    startSessionAs("root");
    long date = new Date().getTime();
    UserStateModel userModel =
                             new UserStateModel("john",
                                                date,
                                                UserStateService.DEFAULT_STATUS);
    UserStateModel userModel2 =
                              new UserStateModel("mary",
                                                 date,
                                                 UserStateService.DEFAULT_STATUS);
    userStateService.save(userModel);
    userStateService.save(userModel2);
    userStateService.ping(userModel.getUserId());
    userStateService.ping(userModel2.getUserId());
    Space spaceTest = getSpaceInstance(0, "root");
    String spaceId = spaceTest.getId();
    spaceTest.setMembers(new String[] { "john" });
    spaceService.updateSpace(spaceTest);
    ContainerResponse response = service("GET", getURLResource("users?status=online&spaceId=" + spaceId), "", null, null);
    assertNotNull(response);
    assertEquals(200, response.getStatus());
    CollectionEntity collections = (CollectionEntity) response.getEntity();
    assertEquals(1, collections.getEntities().size()); // only john
    // non existing space
    response = service("GET", getURLResource("users?status=online&spaceId=600"), "", null, null);
    assertNotNull(response);
    assertEquals(404, response.getStatus());
    ErrorResource errorResource = (ErrorResource) response.getEntity();
    assertEquals("space not found", errorResource.getDeveloperMessage());
    assertEquals("space 600 does not exist", errorResource.getMessage());
  }

  public void testGetUserById() throws Exception {
    startSessionAs("root");
    ContainerResponse response = service("GET", getURLResource("users/john"), "", null, null);
    assertNotNull(response);
    assertEquals(200, response.getStatus());
    ProfileEntity userEntity = getBaseEntity(response.getEntity(), ProfileEntity.class);
    assertEquals("john", userEntity.getUsername());
  }

  public void testGetUserAvatarForAnonymous() throws Exception {
    String user = "john";

    uploadUserAvatar(user);

    Identity identity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, user);
    String avatarUrl = identity.getProfile().getAvatarUrl().replace("/portal/rest", "");

    ContainerResponse response = service("GET", avatarUrl, "", null, null);
    assertNotNull(response);
    assertEquals(200, response.getStatus());

    response = service("GET", getURLResource("users/" + user + "/avatar"), "", null, null);
    assertNotNull(response);
    assertEquals(404, response.getStatus());

    response = service("GET", getURLResource("users/" + LinkProvider.DEFAULT_IMAGE_REMOTE_ID + "/avatar"), "", null, null);
    assertNotNull(response);
    assertEquals(200, response.getStatus());
  }

  public void testGetUserAvatarForAuthentiticatedUser() throws Exception {
    String user = "john";

    uploadUserAvatar(user);

    startSessionAs("mary");

    Identity identity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, user);
    String avatarUrl = identity.getProfile().getAvatarUrl().replace("/portal/rest", "");

    ContainerResponse response = service("GET", avatarUrl, "", null, null);
    assertNotNull(response);
    assertEquals(200, response.getStatus());

    response = service("GET", getURLResource("users/" + user + "/avatar"), "", null, null);
    assertNotNull(response);
    assertEquals(200, response.getStatus());

    response = service("GET", getURLResource("users/" + LinkProvider.DEFAULT_IMAGE_REMOTE_ID + "/avatar"), "", null, null);
    assertNotNull(response);
    assertEquals(200, response.getStatus());
  }

  public void testGetUserBannerForAnonymous() throws Exception {
    String user = "john";

    uploadUserBanner(user);

    Identity identity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, user);
    String bannerUrl = identity.getProfile().getBannerUrl().replace("/portal/rest", "");

    ContainerResponse response = service("GET", bannerUrl, "", null, null);
    assertNotNull(response);
    assertEquals(200, response.getStatus());

    response = service("GET", getURLResource("users/" + user + "/banner"), "", null, null);
    assertNotNull(response);
    assertEquals(404, response.getStatus());

    response = service("GET", getURLResource("users/" + LinkProvider.DEFAULT_IMAGE_REMOTE_ID + "/banner"), "", null, null);
    assertNotNull(response);
    assertEquals(200, response.getStatus());
  }

  public void testGetUserBannerForAuthentiticatedUser() throws Exception {
    String user = "john";

    uploadUserBanner(user);

    startSessionAs("mary");

    Identity identity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, user);
    String bannerUrl = identity.getProfile().getBannerUrl().replace("/portal/rest", "");

    ContainerResponse response = service("GET", bannerUrl, "", null, null);
    assertNotNull(response);
    assertEquals(200, response.getStatus());

    response = service("GET", getURLResource("users/" + user + "/banner"), "", null, null);
    assertNotNull(response);
    assertEquals(200, response.getStatus());

    response = service("GET", getURLResource("users/" + LinkProvider.DEFAULT_IMAGE_REMOTE_ID + "/banner"), "", null, null);
    assertNotNull(response);
    assertEquals(200, response.getStatus());
  }

  public void testGetConnectionsOfUser() throws Exception {
    startSessionAs("root");
    relationshipManager.inviteToConnect(rootIdentity, demoIdentity);
    relationshipManager.confirm(demoIdentity, rootIdentity);

    ContainerResponse response = service("GET", getURLResource("users/root/connections?limit=5&offset=0"), "", null, null);
    assertNotNull(response);
    assertEquals(200, response.getStatus());

    CollectionEntity collections = (CollectionEntity) response.getEntity();
    assertEquals(1, collections.getEntities().size());
    ProfileEntity userEntity = getBaseEntity(collections.getEntities().get(0), ProfileEntity.class);
    assertEquals("demo", userEntity.getUsername());
  }

  public void testGetInvitationsOfUser() throws Exception {
    relationshipManager.inviteToConnect(demoIdentity, rootIdentity);

    startSessionAs("root");
    ContainerResponse response = service("GET", getURLResource("users/connections/invitations?limit=5&offset=0"), "", null, null);
    assertNotNull(response);
    assertEquals(200, response.getStatus());

    CollectionEntity collections = (CollectionEntity) response.getEntity();
    assertEquals(1, collections.getEntities().size());
    ProfileEntity userEntity = getBaseEntity(collections.getEntities().get(0), ProfileEntity.class);
    assertEquals("demo", userEntity.getUsername());
  }

  public void testGetPendingOfUser() throws Exception {
    relationshipManager.inviteToConnect(rootIdentity, demoIdentity);

    startSessionAs("root");
    ContainerResponse response = service("GET", getURLResource("users/connections/pending?limit=5&offset=0"), "", null, null);
    assertNotNull(response);
    assertEquals(200, response.getStatus());

    CollectionEntity collections = (CollectionEntity) response.getEntity();
    assertEquals(1, collections.getEntities().size());
    ProfileEntity userEntity = getBaseEntity(collections.getEntities().get(0), ProfileEntity.class);
    assertEquals("demo", userEntity.getUsername());
  }

  public void testGetActivitiesOfUser() throws Exception {
    startSessionAs("root");
    relationshipManager.inviteToConnect(rootIdentity, demoIdentity);
    relationshipManager.confirm(demoIdentity, rootIdentity);

    ExoSocialActivity rootActivity = new ExoSocialActivityImpl();
    rootActivity.setTitle("root activity");
    activityManager.saveActivityNoReturn(rootIdentity, rootActivity);

    // wait to make sure the order of activities
    Thread.sleep(10);
    ExoSocialActivity demoActivity = new ExoSocialActivityImpl();
    demoActivity.setTitle("demo activity");
    activityManager.saveActivityNoReturn(demoIdentity, demoActivity);

    ExoSocialActivity maryActivity = new ExoSocialActivityImpl();
    maryActivity.setTitle("mary activity");
    activityManager.saveActivityNoReturn(maryIdentity, maryActivity);

    end();
    begin();

    ContainerResponse response = service("GET", getURLResource("users/root/activities?limit=5&offset=0"), "", null, null);
    assertNotNull(response);
    assertEquals(200, response.getStatus());

    CollectionEntity collections = (CollectionEntity) response.getEntity();
    // must return one activity of root and one of demo
    assertEquals(2, collections.getEntities().size());
    ActivityEntity activityEntity = getBaseEntity(collections.getEntities().get(0), ActivityEntity.class);
    assertEquals("demo activity", activityEntity.getTitle());
    activityEntity = getBaseEntity(collections.getEntities().get(1), ActivityEntity.class);
    assertEquals("root activity", activityEntity.getTitle());

    activityManager.deleteActivity(maryActivity);
    activityManager.deleteActivity(demoActivity);
    activityManager.deleteActivity(rootActivity);
  }

  public void testGetSpacesOfUser() throws Exception {
    getSpaceInstance(0, "root");
    getSpaceInstance(1, "john");
    getSpaceInstance(2, "demo");
    getSpaceInstance(3, "demo");
    getSpaceInstance(4, "mary");

    startSessionAs("root");
    ContainerResponse response = service("GET", getURLResource("users/root/spaces?limit=5&offset=0"), "", null, null);
    assertNotNull(response);
    assertEquals(200, response.getStatus());
    CollectionEntity collections = (CollectionEntity) response.getEntity();
    assertEquals(1, collections.getEntities().size());

    startSessionAs("john");
    relationshipManager.inviteToConnect(johnIdentity, demoIdentity);
    relationshipManager.confirm(demoIdentity, johnIdentity);
    response = service("GET", getURLResource("users/john/spaces?limit=5&offset=0"), "", null, null);
    assertNotNull(response);
    assertEquals(200, response.getStatus());
    collections = (CollectionEntity) response.getEntity();
    assertEquals(1, collections.getEntities().size());

    startSessionAs("demo");
    response = service("GET", getURLResource("users/demo/spaces?limit=5&offset=0"), "", null, null);
    assertNotNull(response);
    assertEquals(200, response.getStatus());
    collections = (CollectionEntity) response.getEntity();
    assertEquals(2, collections.getEntities().size());

    startSessionAs("john");
    response = service("GET", getURLResource("users/demo/spaces?limit=5&offset=0"), "", null, null);
    assertNotNull(response);
    assertEquals(200, response.getStatus());

    startSessionAs("john");
    response = service("GET", getURLResource("users/mary/spaces?limit=5&offset=0"), "", null, null);
    assertNotNull(response);
    assertEquals(403, response.getStatus());

    startSessionAs("root");
    response = service("GET", getURLResource("users/demo/spaces?limit=5&offset=0"), "", null, null);
    assertNotNull(response);
    assertEquals(200, response.getStatus());
    collections = (CollectionEntity) response.getEntity();
    assertEquals(2, collections.getEntities().size());
  }

  public void testGetCommonSpaces() throws Exception {
    Space spaceTest = getSpaceInstance(0, "root");
    Space spaceTest1 = getSpaceInstance(1, "demo");

    startSessionAs("root");
    ContainerResponse response = service("GET", getURLResource("users/root/spaces/john?limit=5&offset=0"), "", null, null);
    assertNotNull(response);
    assertEquals(200, response.getStatus());
    CollectionEntity collections = (CollectionEntity) response.getEntity();
    assertEquals(0, collections.getEntities().size());

    spaceTest.setMembers(new String[] { "john" });
    spaceService.updateSpace(spaceTest);
    response = service("GET", getURLResource("users/root/spaces/john?limit=5&offset=0"), "", null, null);
    assertNotNull(response);
    assertEquals(200, response.getStatus());
    collections = (CollectionEntity) response.getEntity();
    assertEquals(1, collections.getEntities().size());

    startSessionAs("john");
    response = service("GET", getURLResource("users/john/spaces/root?limit=5&offset=0"), "", null, null);
    assertNotNull(response);
    assertEquals(200, response.getStatus());
    collections = (CollectionEntity) response.getEntity();
    assertEquals(1, collections.getEntities().size());

    spaceTest1.setMembers(new String[] { "john", "root" });
    spaceService.updateSpace(spaceTest1);

    response = service("GET", getURLResource("users/john/spaces/root?limit=5&offset=0"), "", null, null);
    assertNotNull(response);
    assertEquals(200, response.getStatus());
    collections = (CollectionEntity) response.getEntity();
    assertEquals(2, collections.getEntities().size());

    startSessionAs("demo");
    response = service("GET", getURLResource("users/john/spaces/root?limit=5&offset=0"), "", null, null);
    assertNotNull(response);
    assertEquals(403, response.getStatus());
  }

  public void testAddActivityByUser() throws Exception {
    String input = "{\"title\":titleOfActivity,\"templateParams\":{\"param1\": value1,\"param2\":value2}}";
    startSessionAs("john");
    ContainerResponse response = getResponse("POST", getURLResource("users/john/activities"), input);
    assertNotNull(response);
    assertEquals(200, response.getStatus());
    DataEntity responseEntity = (DataEntity) response.getEntity();
    String title = (String) responseEntity.get("title");
    assertNotNull(title);
    assertEquals("titleOfActivity", title);
    DataEntity templateParams = (DataEntity) responseEntity.get("templateParams");
    assertNotNull(templateParams);
    assertEquals(2, templateParams.size());
  }

  public void testUpdateProfileAtribute() throws Exception {
    startSessionAs("root");
    String email = "root@test.com";
    byte[] formData = ("name=email&value=" + email).getBytes();
    MultivaluedMap<String, String> headers = new MultivaluedMapImpl();
    headers.putSingle("Content-Type", "application/x-www-form-urlencoded");
    ContainerResponse response = service("PATCH", getURLResource("users/root/"), "", headers, formData);
    assertNotNull(response);
    assertEquals(String.valueOf(response.getEntity()), 204, response.getStatus());

    Identity identity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, "root");
    assertNotNull(identity);
    assertEquals(email, identity.getProfile().getEmail());

    response = service("PATCH", getURLResource("users/john/"), "", headers, formData);
    assertNotNull(response);
    assertEquals("User root shouldn't be able to modify john attributes", 401, response.getStatus());
  }

  public void testUpdateProfileAvatar() throws Exception {
    startSessionAs("root");

    String uploadId = "testtest";
    byte[] formData = ("name=avatar&value=" + uploadId).getBytes();
    MultivaluedMap<String, String> headers = new MultivaluedMapImpl();
    headers.putSingle("Content-Type", "application/x-www-form-urlencoded");
    ContainerResponse response = service("PATCH", getURLResource("users/root/"), "", headers, formData);
    assertNotNull(response);
    assertEquals(String.valueOf(response.getEntity()), 500, response.getStatus());

    URL resource = getClass().getClassLoader().getResource("blank.gif");
    uploadService.createUploadResource(uploadId, resource.getFile(), "avatar.png", "image/png");
    response = service("PATCH", getURLResource("users/root/"), "", headers, formData);
    assertNotNull(response);
    assertEquals(String.valueOf(response.getEntity()), 204, response.getStatus());

    Identity identity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, "root");
    assertNotNull(identity);
    assertNotNull(identity.getProfile().getAvatarLastUpdated());

    InputStream avatarInputStream = identityManager.getAvatarInputStream(identity);
    String storedContent = IOUtil.getStreamContentAsString(avatarInputStream);
    String content = IOUtil.getStreamContentAsString(getClass().getClassLoader().getResourceAsStream("blank.gif"));
    assertEquals(content, storedContent);
  }

  public void testUpdateProfileBanner() throws Exception {
    startSessionAs("root");

    String uploadId = "testtest";
    byte[] formData = ("name=banner&value=" + uploadId).getBytes();
    MultivaluedMap<String, String> headers = new MultivaluedMapImpl();
    headers.putSingle("Content-Type", "application/x-www-form-urlencoded");
    ContainerResponse response = service("PATCH", getURLResource("users/root/"), "", headers, formData);
    assertNotNull(response);
    assertEquals(String.valueOf(response.getEntity()), 500, response.getStatus());

    URL resource = getClass().getClassLoader().getResource("blank.gif");
    uploadService.createUploadResource(uploadId, resource.getFile(), "banner.png", "image/png");
    response = service("PATCH", getURLResource("users/root/"), "", headers, formData);
    assertNotNull(response);
    assertEquals(String.valueOf(response.getEntity()), 204, response.getStatus());

    Identity identity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, "root");
    assertNotNull(identity);
    assertNotNull(identity.getProfile().getBannerLastUpdated());

    InputStream bannerInputStream = identityManager.getBannerInputStream(identity);
    String storedContent = IOUtil.getStreamContentAsString(bannerInputStream);
    String content = IOUtil.getStreamContentAsString(getClass().getClassLoader().getResourceAsStream("blank.gif"));
    assertEquals(content, storedContent);
  }


  public void testImportUsers() throws Exception {
    startSessionAs("root");

    String uploadId = "users-empty.csv";
    MultivaluedMap<String, String> headers = new MultivaluedMapImpl();
    headers.putSingle("Content-Type", "application/x-www-form-urlencoded");
    ContainerResponse response = service("POST",
                                         getURLResource("users/csv"),
                                         "",
                                         headers,
                                         ("uploadId=" + uploadId + "&sync=true").getBytes());
    assertNotNull(response);

    assertEquals(404, response.getStatus());

    URL resource = getClass().getClassLoader().getResource("users-empty.csv");
    uploadService.createUploadResource(uploadId, resource.getFile(), "users-empty.csv", "text/csv");
    response = service("POST", getURLResource("users/csv"), "", headers, ("uploadId=" + uploadId + "&sync=true").getBytes());
    assertNotNull(response);
    assertEquals(400, response.getStatus());

    uploadId = "users.csv";
    resource = getClass().getClassLoader().getResource("users.csv");
    uploadService.createUploadResource(uploadId, resource.getFile(), "users.csv", "text/csv");
    response = service("POST", getURLResource("users/csv"), "", headers, ("uploadId=" + uploadId + "&sync=true").getBytes());
    assertNotNull(response);
    assertNull(response.getEntity());
    assertEquals(204, response.getStatus());

    uploadId = "users-update.csv";
    resource = getClass().getClassLoader().getResource("users-update.csv");
    uploadService.createUploadResource(uploadId, resource.getFile(), "users-update.csv", "text/csv");
    response = service("POST", getURLResource("users/csv"), "", headers, ("uploadId=" + uploadId + "&sync=true").getBytes());
    assertNotNull(response);
    assertNull(response.getEntity());
    assertEquals(204, response.getStatus());

    uploadId = "users.csv";
    response = service("POST", getURLResource("users/csv"), "", headers, ("uploadId=" + uploadId + "&progress=true").getBytes());
    assertNotNull(response);
    assertNotNull(response.getEntity());
    assertEquals(200, response.getStatus());
    assertEquals(response.getEntity().getClass(), UserImportResultEntity.class);
    UserImportResultEntity importResultEntity = (UserImportResultEntity) response.getEntity();
    assertEquals(4, importResultEntity.getCount());
    assertEquals(importResultEntity.getCount(), importResultEntity.getProcessedCount());
    UploadResource uploadResource = uploadService.getUploadResource(uploadId);

    BufferedReader reader = new BufferedReader(new FileReader(uploadResource.getStoreLocation()));
    reader.readLine();
    String User = reader.readLine();
    List<String> userNames = new ArrayList<>();
    List<String> passWords = new ArrayList<>();

    while (User!= null){
    List<String> userProperties = Arrays.asList(User.split(","));
     userNames.add(userProperties.get(0));
     passWords.add(userProperties.get(3));
     User = reader.readLine();
    }

    assertNull(importResultEntity.getErrorMessages());
    assertNull(importResultEntity.getWarnMessages());

    uploadId = "users-update.csv";
    response = service("POST", getURLResource("users/csv"), "", headers, ("uploadId=" + uploadId + "&progress=true").getBytes());
    assertNotNull(response);
    assertNotNull(response.getEntity());
    assertEquals(200, response.getStatus());
    assertEquals(response.getEntity().getClass(), UserImportResultEntity.class);
    importResultEntity = (UserImportResultEntity) response.getEntity();
    assertEquals(4, importResultEntity.getCount());
    assertEquals(importResultEntity.getCount(), importResultEntity.getProcessedCount());

    assertNull(importResultEntity.getErrorMessages());
    assertNull(importResultEntity.getWarnMessages());

    for(int i=0;i<(userNames.size());i++) {
    boolean result = organizationService.getUserHandler().authenticate(userNames.get(i), passWords.get(i));
    assertTrue(result);
    }

    uploadId = "users.csv";
    uploadResource = uploadService.getUploadResource(uploadId);
    assertNotNull(uploadResource);
    response = service("POST", getURLResource("users/csv"), "", headers, ("uploadId=" + uploadId + "&clean=true").getBytes());
    assertNotNull(response);
    assertNotNull(response.getEntity());
    assertEquals(200, response.getStatus());
    assertEquals(response.getEntity().getClass(), UserImportResultEntity.class);
    importResultEntity = (UserImportResultEntity) response.getEntity();
    assertEquals(4, importResultEntity.getCount());
    assertNull(importResultEntity.getErrorMessages());
    assertNull(importResultEntity.getWarnMessages());
    uploadResource = uploadService.getUploadResource(uploadId);
    assertNull(uploadResource);

    uploadId = "users-errors.csv";
    resource = getClass().getClassLoader().getResource("users-errors.csv");
    uploadService.createUploadResource(uploadId, resource.getFile(), "users-errors.csv", "text/csv");
    response = service("POST", getURLResource("users/csv"), "", headers, ("uploadId=" + uploadId + "&sync=true").getBytes());
    assertNotNull(response);
    assertNull(response.getEntity());
    assertEquals(204, response.getStatus());

    response = service("POST", getURLResource("users/csv"), "", headers, ("uploadId=" + uploadId + "&progress=true").getBytes());
    assertNotNull(response);
    assertNotNull(response.getEntity());
    assertEquals(200, response.getStatus());
    assertEquals(response.getEntity().getClass(), UserImportResultEntity.class);
    importResultEntity = (UserImportResultEntity) response.getEntity();
    assertEquals(6, importResultEntity.getCount());
    assertEquals(importResultEntity.getCount(), importResultEntity.getProcessedCount());
    assertNotNull(importResultEntity.getErrorMessages());
    assertEquals(3, importResultEntity.getErrorMessages().size());
    assertNotNull(importResultEntity.getWarnMessages());
    assertEquals(1, importResultEntity.getWarnMessages().size());
  }

  public void testUpdateProfileAtributes() throws Exception {
    String firstName = "Johnny";
    String lastName = "Bravo";
    String fullName = "Johnny Bravo";
    String aboutMe = "AboutMe";
    String imType = "Skype";
    String imId = "johnnyB";
    String phoneType = "work";
    String phoneNumber = "123456";
    String url = "fakeURL";
    String email = "johnny@localhost.com";

    StringBuilder input = new StringBuilder("{");
    input.append("\"");
    input.append(ProfileEntity.FIRSTNAME);
    input.append("\":\"");
    input.append(firstName);
    input.append("\",");

    input.append("\"");
    input.append(ProfileEntity.LASTNAME);
    input.append("\":\"");
    input.append(lastName);
    input.append("\",");

    input.append("\"");
    input.append(ProfileEntity.FULLNAME);
    input.append("\":\"");
    input.append(fullName);
    input.append("\",");

    input.append("\"");
    input.append(ProfileEntity.EMAIL);
    input.append("\":\"");
    input.append(email);
    input.append("\",");

    input.append("\"");
    input.append(ProfileEntity.ABOUT_ME);
    input.append("\":\"");
    input.append(aboutMe);
    input.append("\",");

    input.append("\"");
    input.append(ProfileEntity.PHONES);
    input.append("\": [{\"phoneType\":\"");
    input.append(phoneType);
    input.append("\",\"phoneNumber\":\"");
    input.append(phoneNumber);
    input.append("\"}],");

    input.append("\"");
    input.append(ProfileEntity.IMS);
    input.append("\": [{\"imType\":\"");
    input.append(imType);
    input.append("\", \"imId\":\"");
    input.append(imId);
    input.append("\"}],");

    input.append("\"");
    input.append(ProfileEntity.URLS);
    input.append("\":[{\"url\":\"");
    input.append(url);
    input.append("\"}]");
    input.append("}");

    startSessionAs("john");
    ContainerResponse response = getResponse("PATCH", getURLResource("users/john/profile"), input.toString());
    assertNotNull(response);
    assertEquals(String.valueOf(response.getEntity()), 204, response.getStatus());

    Identity identity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, "john");
    assertNotNull(identity);
    assertEquals(firstName, identity.getProfile().getProperty(Profile.FIRST_NAME));
    assertEquals(lastName, identity.getProfile().getProperty(Profile.LAST_NAME));
    assertEquals(fullName, identity.getProfile().getProperty(Profile.FULL_NAME));

    List<Map<String, String>> phones = identity.getProfile().getPhones();
    assertNotNull(phones);
    assertEquals(1, phones.size());
    assertNotNull(phoneType, phones.get(0).get("key"));
    assertNotNull(phoneNumber, phones.get(0).get("value"));

    List<Map<String, String>> ims = (List<Map<String, String>>) identity.getProfile().getProperty(Profile.CONTACT_IMS);
    assertNotNull(ims);
    assertEquals(1, ims.size());
    assertNotNull(imType, ims.get(0).get("key"));
    assertNotNull(imId, ims.get(0).get("value"));

    List<Map<String, String>> urls = (List<Map<String, String>>) identity.getProfile().getProperty(Profile.CONTACT_URLS);
    assertNotNull(urls);
    assertEquals(1, urls.size());
    assertNotNull(url, urls.get(0).get("value"));
  }

  public void testUpdateUserFieldsWithValidators() throws Exception {
    MultivaluedMap<String, String> headers = new MultivaluedMapImpl();
    headers.putSingle("Content-Type", "application/x-www-form-urlencoded");

    String johnEmail = "john@platform.com";
    startSessionAs("john");
    ContainerResponse response = service("PATCH",
                                         getURLResource("users/john"),
                                         "",
                                         headers,
                                         ("name=" + ProfileEntity.EMAIL + "&value=" + johnEmail).getBytes());
    assertNotNull(response);
    assertEquals(204, response.getStatus());

    String user = "demo";
    startSessionAs(user);
    response = service("PATCH",
                       getURLResource("users/john"),
                       "",
                       headers,
                       ("name=" + ProfileEntity.FIRSTNAME + "&value=t").getBytes());
    assertNotNull(response);
    assertEquals("demo shouldn't be allowed to update john fields. Response content: " + response.getEntity(),
                 401,
                 response.getStatus());

    response = service("PATCH",
                       getURLResource("users/" + user),
                       "",
                       headers,
                       ("name=" + ProfileEntity.EMAIL + "&value=WRONG_FROMAT").getBytes());
    assertNotNull(response);
    assertEquals("Email format validation should return HTTP 400 code. Response content: " + response.getEntity(),
                 400,
                 response.getStatus());

    response = service("PATCH",
                       getURLResource("users/" + user),
                       "",
                       headers,
                       ("name=" + ProfileEntity.EMAIL + "&value=" + johnEmail).getBytes());
    assertNotNull(response);
    assertEquals("Email already exists return HTTP 401 code. Response content: " + response.getEntity(),
                 401,
                 response.getStatus());

    response = service("PATCH",
                       getURLResource("users/" + user),
                       "",
                       headers,
                       ("name=" + ProfileEntity.FIRSTNAME + "&value=D").getBytes());
    assertNotNull(response);
    assertEquals("FIRST name format validation should return HTTP 400 code. Response content: " + response.getEntity(),
                 400,
                 response.getStatus());

    response = service("PATCH",
                       getURLResource("users/" + user),
                       "",
                       headers,
                       ("name=" + ProfileEntity.LASTNAME + "&value=T").getBytes());
    assertNotNull(response);
    assertEquals("LAST name format validation should return HTTP 400 code. Response content: " + response.getEntity(),
                 400,
                 response.getStatus());
  }

  public void testUpdateUserFieldWithValidators() throws Exception {
    String johnEmail = "john@platform.com";
    startSessionAs("john");
    JSONObject johnData = new JSONObject();
    johnData.put(ProfileEntity.EMAIL, johnEmail);
    ContainerResponse response = getResponse("PATCH", "/v1/social/users/john/profile", johnData.toString());
    assertNotNull(response);
    assertEquals(204, response.getStatus());

    JSONObject data = new JSONObject();
    data.put(ProfileEntity.USERNAME, "demo");
    data.put(ProfileEntity.FIRSTNAME, "Demo");
    data.put(ProfileEntity.LASTNAME, "Test");
    data.put(ProfileEntity.EMAIL, "demo@test.com");

    String user = "demo";
    startSessionAs(user);
    response = getResponse("PATCH", "/v1/social/users/john/profile", data.toString());
    assertNotNull(response);
    assertEquals("demo shouldn't be allowed to update john fields. Response content: " + response.getEntity(),
                 401,
                 response.getStatus());
    data.put(ProfileEntity.USERNAME, user);

    data.put(ProfileEntity.EMAIL, "WRONG_FORMAT");
    response = getResponse("PATCH", "/v1/social/users/" + user + "/profile", data.toString());
    assertNotNull(response);
    assertEquals("Email format validation should return HTTP 400 code. Response content: " + response.getEntity(),
                 400,
                 response.getStatus());

    startSessionAs(user);
    data.put(ProfileEntity.EMAIL, johnEmail);
    response = getResponse("PATCH", "/v1/social/users/" + user + "/profile", data.toString());
    assertNotNull(response);
    assertEquals("Email already exists return HTTP 401 code. Response content: " + response.getEntity(),
                 401,
                 response.getStatus());
    data.put(ProfileEntity.EMAIL, "demo@test.com");

    data.put(ProfileEntity.FIRSTNAME, "d");
    response = getResponse("PATCH", "/v1/social/users/" + user + "/profile", data.toString());
    assertNotNull(response);
    assertEquals("FIRST name format validation should return HTTP 400 code. Response content: " + response.getEntity(),
                 400,
                 response.getStatus());
    data.put(ProfileEntity.FIRSTNAME, "Demo");

    data.put(ProfileEntity.LASTNAME, "t");
    response = getResponse("PATCH", "/v1/social/users/" + user + "/profile", data.toString());
    assertNotNull(response);
    assertEquals("LAST name format validation should return HTTP 400 code. Response content: " + response.getEntity(),
                 400,
                 response.getStatus());
    data.put(ProfileEntity.LASTNAME, "Test");
  }

  private Space getSpaceInstance(int number, String creator) throws Exception {
    Space space = new Space();
    space.setDisplayName("my space " + number);
    space.setPrettyName(space.getDisplayName());
    space.setRegistration(Space.OPEN);
    space.setDescription("add new space " + number);
    space.setType(DefaultSpaceApplicationHandler.NAME);
    space.setVisibility(Space.PUBLIC);
    space.setRegistration(Space.VALIDATION);
    space.setPriority(Space.INTERMEDIATE_PRIORITY);
    space.setGroupId("/spaces/space" + number);
    String[] managers = new String[] { creator };
    String[] members = new String[] { creator };
    space.setManagers(managers);
    space.setMembers(members);
    space.setUrl(space.getPrettyName());
    this.spaceService.createSpace(space, creator);
    return space;
  }

  private void uploadUserBanner(String user) throws Exception {
    startSessionAs(user);
    String uploadId = "testtest";
    byte[] formData = ("name=banner&value=" + uploadId).getBytes();
    MultivaluedMap<String, String> headers = new MultivaluedMapImpl();
    headers.putSingle("Content-Type", "application/x-www-form-urlencoded");
    URL resource = getClass().getClassLoader().getResource("blank.gif");
    uploadService.createUploadResource(uploadId, resource.getFile(), "banner.png", "image/png");
    ContainerResponse response = service("PATCH", getURLResource("users/" + user), "", headers, formData);
    assertNotNull(response);
    assertEquals(String.valueOf(response.getEntity()), 204, response.getStatus());
    endSession();
  }


  private void uploadUserAvatar(String user) throws Exception {
    startSessionAs(user);
    String uploadId = "testtest";
    byte[] formData = ("name=avatar&value=" + uploadId).getBytes();
    MultivaluedMap<String, String> headers = new MultivaluedMapImpl();
    headers.putSingle("Content-Type", "application/x-www-form-urlencoded");
    URL resource = getClass().getClassLoader().getResource("blank.gif");
    uploadService.createUploadResource(uploadId, resource.getFile(), "avatar.png", "image/png");
    ContainerResponse response = service("PATCH", getURLResource("users/" + user), "", headers, formData);
    assertNotNull(response);
    assertEquals(String.valueOf(response.getEntity()), 204, response.getStatus());
    endSession();
  }
}
