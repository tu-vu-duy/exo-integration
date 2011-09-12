/*
 * Copyright (C) 2003-2011 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 */
package org.exoplatform.wcm.ext.component.activity;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.portlet.PortletRequest;

import org.exoplatform.commons.utils.ISO8601;
import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.ecm.webui.utils.Utils;
import org.exoplatform.portal.application.PortalRequestContext;
import org.exoplatform.portal.mop.SiteType;
import org.exoplatform.portal.mop.user.UserNavigation;
import org.exoplatform.portal.mop.user.UserNode;
import org.exoplatform.portal.mop.user.UserPortal;
import org.exoplatform.portal.webui.util.Util;
import org.exoplatform.services.cms.BasePath;
import org.exoplatform.services.cms.drives.DriveData;
import org.exoplatform.services.cms.drives.ManageDriveService;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.wcm.core.NodeLocation;
import org.exoplatform.services.wcm.friendly.FriendlyService;
import org.exoplatform.services.wcm.utils.WCMCoreUtils;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.webui.activity.BaseUIActivity;
import org.exoplatform.web.url.navigation.NodeURL;
import org.exoplatform.webui.application.WebuiRequestContext;
import org.exoplatform.webui.application.portlet.PortletRequestContext;
import org.exoplatform.webui.config.annotation.ComponentConfig;
import org.exoplatform.webui.config.annotation.EventConfig;
import org.exoplatform.webui.core.lifecycle.UIFormLifecycle;

/**
 * Created by The eXo Platform SAS Author : eXoPlatform exo@exoplatform.com Mar
 * 15, 2011
 */
@ComponentConfig(lifecycle = UIFormLifecycle.class, template = "classpath:groovy/ecm/social-integration/plugin/space/ContentUIActivity.gtmpl", events = {
    @EventConfig(listeners = BaseUIActivity.ToggleDisplayLikesActionListener.class),
    @EventConfig(listeners = BaseUIActivity.ToggleDisplayCommentFormActionListener.class),
    @EventConfig(listeners = BaseUIActivity.LikeActivityActionListener.class),
    @EventConfig(listeners = BaseUIActivity.SetCommentListStatusActionListener.class),
    @EventConfig(listeners = BaseUIActivity.PostCommentActionListener.class),
    @EventConfig(listeners = BaseUIActivity.DeleteActivityActionListener.class, confirm = "UIActivity.msg.Are_You_Sure_To_Delete_This_Activity"),
    @EventConfig(listeners = BaseUIActivity.DeleteCommentActionListener.class, confirm = "UIActivity.msg.Are_You_Sure_To_Delete_This_Comment") })
public class ContentUIActivity extends BaseUIActivity {

  private static final String PATH_PARAM = "path";

  private static final String SITE_EXPLORER = "siteExplorer";

  private static final String NEW_DATE_FORMAT = "hh:mm:ss MMM d, yyyy";

  private static final Log   log           = ExoLogger.getLogger(ContentUIActivity.class);

  public static final String ACTIVITY_TYPE = "CONTENT_ACTIVITY";

  public static final String ID            = "id";

  public static final String CONTENT_LINK  = "contenLink";

  public static final String MESSAGE       = "message";

  public static final String REPOSITORY    = "repository";

  public static final String WORKSPACE     = "workspace";

  public static final String CONTENT_NAME  = "contentName";

  public static final String IMAGE_PATH    = "imagePath";

  public static final String MIME_TYPE     = "mimeType";

  public static final String STATE         = "state";

  public static final String AUTHOR        = "author";

  public static final String DATE_CREATED  = "dateCreated";

  public static final String LAST_MODIFIED = "lastModified";

  private String             contentLink;

  private String             message;

  private String             contentName;

  private String             imagePath;

  private String             mimeType;

  private String             nodeUUID;

  private String             state;

  private String             author;

  private String             dateCreated;

  private String             lastModified;

  private Node               contentNode;

  private NodeLocation       nodeLocation;

  public ContentUIActivity() throws Exception {
    super();
  }

  public String getContentLink() {
    return contentLink;
  }

  public void setContentLink(String contentLink) {
    this.contentLink = contentLink;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public String getContentName() {
    return contentName;
  }

  public void setContentName(String contentName) {
    this.contentName = contentName;
  }

  public String getImagePath() {
    return imagePath;
  }

  public void setImagePath(String imagePath) {
    this.imagePath = imagePath;
  }

  public String getMimeType() {
    return mimeType;
  }

  public void setMimeType(String mimeType) {
    this.mimeType = mimeType;
  }

  public String getNodeUUID() {
    return nodeUUID;
  }

  public void setNodeUUID(String nodeUUID) {
    this.nodeUUID = nodeUUID;
  }

  public String getState() {
    return state;
  }

  public void setState(String state) {
    this.state = state;
  }

  public String getAuthor() {
    return author;
  }

  public void setAuthor(String author) {
    this.author = author;
  }
  
  private String convertDateFormat(String strDate, String strOldFormat, String strNewFormat) throws ParseException {
    if (strDate == null || strDate.length() <= 0) {
      return "";
    }
    Locale locale = Util.getPortalRequestContext().getLocale();
    SimpleDateFormat sdfSource = new SimpleDateFormat(strOldFormat);
    SimpleDateFormat sdfDestination = new SimpleDateFormat(strNewFormat, locale);
    Date date = sdfSource.parse(strDate);
    return sdfDestination.format(date);
  }

  public String getDateCreated() throws ParseException {
    return convertDateFormat(dateCreated, ISO8601.SIMPLE_DATETIME_FORMAT, NEW_DATE_FORMAT);
  }  

  public void setDateCreated(String dateCreated) {
    this.dateCreated = dateCreated;
  }

  public String getLastModified() throws ParseException {
    return convertDateFormat(lastModified, ISO8601.SIMPLE_DATETIME_FORMAT, NEW_DATE_FORMAT);
  }

  public void setLastModified(String lastModified) {
    this.lastModified = lastModified;
  }

  public Node getContentNode() {
    return contentNode;
  }

  public void setContentNode(Node contentNode) {
    this.contentNode = contentNode;
  }

  public NodeLocation getNodeLocation() {
    return nodeLocation;
  }

  public void setNodeLocation(NodeLocation nodeLocation) {
    this.nodeLocation = nodeLocation;
  }

  /**
   * Gets the summary.
   * 
   * @param node the node
   * @return the summary
   * @throws Exception the exception
   */
  public String getSummary(Node node) throws Exception {
    String desc = "";
    if (node != null) {
      if (node.hasProperty("exo:summary")) {
        desc = node.getProperty("exo:summary").getValue().getString();
      } else if (node.hasNode("jcr:content")) {
        Node content = node.getNode("jcr:content");
        if (content.hasProperty("dc:description")) {
          try {
            desc = content.getProperty("dc:description").getValues()[0].getString();
          } catch (Exception ex) {
            return "";
          }
        }
      }
    }
    return desc;
  }

  public String getUserFullName(String userId) {
    ExoContainer container = ExoContainerContext.getCurrentContainer();
    IdentityManager identityManager = (IdentityManager) container.getComponentInstanceOfType(IdentityManager.class);
    
    return identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, userId, true).getProfile().getFullName();
  }

  public String getUserProfileUri(String userId) {
    ExoContainer container = ExoContainerContext.getCurrentContainer();
    IdentityManager identityManager = (IdentityManager) container.getComponentInstanceOfType(IdentityManager.class);
    
    return identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, userId, true).getProfile().getUrl();
  }

  public String getUserAvatarImageSource(String userId) {
    return getOwnerIdentity().getProfile().getAvatarUrl();
  }

  public String getSpaceAvatarImageSource(String spaceIdentityId) {
    try {
      String spaceId = getOwnerIdentity().getRemoteId();
      SpaceService spaceService = getApplicationComponent(SpaceService.class);
      Space space = spaceService.getSpaceById(spaceId);
      if (space != null) {
        return space.getAvatarUrl();
      }
    } catch (Exception e) {
      log.warn("Failed to getSpaceById: " + spaceIdentityId, e);
    }
    return null;
  }

  public void setUIActivityData(Map<String, String> activityParams) {
    this.contentLink = activityParams.get(ContentUIActivity.CONTENT_LINK);
    this.nodeUUID = activityParams.get(ContentUIActivity.ID);
    this.state = activityParams.get(ContentUIActivity.STATE);
    this.author = activityParams.get(ContentUIActivity.AUTHOR);
    this.dateCreated = activityParams.get(ContentUIActivity.DATE_CREATED);
    this.lastModified = activityParams.get(ContentUIActivity.LAST_MODIFIED);
    this.contentName = activityParams.get(ContentUIActivity.CONTENT_NAME);
    this.message = activityParams.get(ContentUIActivity.MESSAGE);
    this.mimeType = activityParams.get(ContentUIActivity.MIME_TYPE);
    this.imagePath = activityParams.get(ContentUIActivity.IMAGE_PATH);
  }
  
  /**
   * Generate the viewer link to site explorer by item path
   * 
   * @param String the item path
   * @return String the viewer link
   * @throws Exception 
   */
  public String getViewableLink() throws Exception {
    String[] params = this.contentLink.split("/");
    String repository = params[0];
    String workspace = params[1];
    String nodePath = this.contentLink.substring((repository + "/" + workspace).length());

    PortalRequestContext pContext = Util.getPortalRequestContext();
    NodeURL nodeURL = pContext.createURL(NodeURL.TYPE);

    ManageDriveService manageDriveService = WCMCoreUtils.getService(ManageDriveService.class);
    List<DriveData> driveList = manageDriveService.getDriveByUserRoles(pContext.getRemoteUser(), Utils.getMemberships());
    DriveData drive = getDrive(driveList, workspace, nodePath);
    if (drive == null) {
      UserNode siteExNode = getUserNodeByURI(SITE_EXPLORER);
      nodeURL.setNode(siteExNode);
      nodeURL.setQueryParameterValue(PATH_PARAM, this.contentLink);
      return nodeURL.toString();
    }

    String driverName = drive.getName();
    String nodePathInDrive = "/".equals(drive.getHomePath()) ? nodePath : nodePath.substring(drive.getHomePath().length());
    if (drive.getHomePath().contains("/spaces/")) {
      String spaceName = driverName.replace(".spaces.", "");
      UserNode documentNode = getUserNodeByURI(spaceName + "/documents");
      nodeURL.setNode(documentNode);
    } else {
      UserNode siteExNode = getUserNodeByURI(SITE_EXPLORER);
      nodeURL.setNode(siteExNode);
    }

    nodeURL.setQueryParameterValue(PATH_PARAM, repository + "/" + driverName + nodePathInDrive);
    return nodeURL.toString();
  }
  
  private UserNode getUserNodeByURI(String uri) {
    UserPortal userPortal = Util.getPortalRequestContext().getUserPortalConfig().getUserPortal();
    List<UserNavigation> allNavs = userPortal.getNavigations();

    for (UserNavigation nav : allNavs) {
      if (nav.getKey().getType().equals(SiteType.GROUP)) {
        UserNode userNode = userPortal.resolvePath(nav, null, uri);
        if (userNode != null) {
          return userNode;
        }
      }
    }
    return null;
  }
  
  /**
   * Gets the webdav url.
   * 
   * @param node the node
   * @return the webdav url
   * @throws Exception the exception
   */
  public String getWebdavURL() throws Exception {
    PortletRequestContext portletRequestContext = WebuiRequestContext.getCurrentInstance();
    PortletRequest portletRequest = portletRequestContext.getRequest();
    NodeLocation nodeLocation = NodeLocation.getNodeLocationByNode(this.contentNode);
    String repository = nodeLocation.getRepository();
    String workspace = nodeLocation.getWorkspace();
    String baseURI = portletRequest.getScheme() + "://" + portletRequest.getServerName() + ":"
        + String.format("%s", portletRequest.getServerPort());

    FriendlyService friendlyService = WCMCoreUtils.getService(FriendlyService.class);
    String link = "#";

    String portalName = PortalContainer.getCurrentPortalContainerName();
    String restContextName = PortalContainer.getCurrentRestContextName();
    if (this.contentNode.isNodeType("nt:frozenNode")) {
      String uuid = this.contentNode.getProperty("jcr:frozenUuid").getString();
      Node originalNode = this.contentNode.getSession().getNodeByUUID(uuid);
      link = baseURI + "/" + portalName + "/" + restContextName + "/jcr/" + repository + "/"
          + workspace + originalNode.getPath() + "?version=" + this.contentNode.getParent().getName();
    } else {
      link = baseURI + "/" + portalName + "/" + restContextName + "/jcr/" + repository + "/"
          + workspace + this.contentNode.getPath();
    }

    return friendlyService.getFriendlyUri(link);
  }
  
  private DriveData getDrive(List<DriveData> lstDrive, String workspace, String nodePath) throws RepositoryException{
    
    DriveData driveData = null;
    String[] elements = nodePath.split("/");
    String driveMapper = "";
    boolean found = false;
    NodeHierarchyCreator nodeHierarchyCreator = (NodeHierarchyCreator) ExoContainerContext.getCurrentContainer()
                                                                                          .getComponentInstanceOfType(NodeHierarchyCreator.class);
    String groupPath = nodeHierarchyCreator.getJcrPath(BasePath.CMS_GROUPS_PATH);
    String spacesFolder = groupPath + "/spaces";
    for (int i = 0; i < elements.length; i++) {
      /**
       * append elements in node path one by one to compare with driver
       * example: node path = /a/b/c/d
       * i == 0 => driveMapper = "/" (root node)
       * i == 1 => driveMapper = "/a"
       * i == 2 => driveMapper = "/a/b" 
       * ...
       */      
      if (i == 1) {
        driveMapper += elements[i];
      } else {
        driveMapper += "/" + elements[i];
      }      
      for (DriveData drive : lstDrive) {        
        if (workspace.equals(drive.getWorkspace()) && driveMapper.equals(drive.getHomePath())) {
          driveData = drive;
          if (!nodePath.startsWith(spacesFolder)) {
            found = true;
          }
          break;          
        }
      }
      if (found) {
        break;
      }    
    } 
    return driveData;
  }
}
