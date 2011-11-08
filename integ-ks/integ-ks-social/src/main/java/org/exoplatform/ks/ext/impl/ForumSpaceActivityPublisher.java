/*
 * Copyright (C) 2003-2010 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 */
package org.exoplatform.ks.ext.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.forum.service.Category;
import org.exoplatform.forum.service.Forum;
import org.exoplatform.forum.service.ForumEventListener;
import org.exoplatform.forum.service.ForumService;
import org.exoplatform.forum.service.Post;
import org.exoplatform.forum.service.Topic;
import org.exoplatform.forum.service.Utils;
import org.exoplatform.ks.bbcode.core.ExtendedBBCodeProvider;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.social.core.activity.model.ExoSocialActivity;
import org.exoplatform.social.core.activity.model.ExoSocialActivityImpl;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.manager.ActivityManager;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

/**
 * @author <a href="mailto:patrice.lamarque@exoplatform.com">Patrice Lamarque</a>
 * @version $Revision$
 */
public class ForumSpaceActivityPublisher extends ForumEventListener {

  public static final String FORUM_APP_ID      = "ks-forum:spaces";

  public static final String FORUM_ID_KEY      = "ForumId";

  public static final String CATE_ID_KEY       = "CateId";

  public static final String ACTIVITY_TYPE_KEY = "ActivityType";

  public static final String POST_TYPE         = "Post";

  public static final String POST_ID_KEY       = "PostId";

  public static final String POST_OWNER_KEY    = "PostOwner";

  public static final String POST_LINK_KEY     = "PostLink";

  public static final String POST_NAME_KEY     = "PostName";

  public static final String TOPIC_ID_KEY      = "TopicId";

  public static final String TOPIC_OWNER_KEY   = "TopicOwner";

  public static final String TOPIC_LINK_KEY    = "TopicLink";

  public static final String TOPIC_NAME_KEY    = "TopicName";
  
  private static final int   TYPE_PRIVATE      = 2;

  private static Log         LOG               = ExoLogger.getExoLogger(ForumSpaceActivityPublisher.class);

  @Override
  public void saveCategory(Category category) {
  }

  @Override
  public void saveForum(Forum forum) {
  }

  public static enum ACTIVITYTYPE {
    AddPost, AddTopic, UpdatePost, UpdateTopic
  }
  
  private void saveActivity(Identity ownerStream, ExoSocialActivity activity) throws Exception {
    ActivityManager activityM = (ActivityManager) ExoContainerContext.getCurrentContainer().getComponentInstanceOfType(ActivityManager.class);
    activityM.saveActivityNoReturn(ownerStream, activity);
  }
  
  private ExoSocialActivity activity(Identity author, String title, String body, String forumId, String categoryId, String topicId, String type, Map<String, String> templateParams) throws Exception {
    ExoSocialActivity activity = new ExoSocialActivityImpl();
    body = ForumTransformHTML.getTitleInHTMLCode(body, new ArrayList<String>((new ExtendedBBCodeProvider()).getSupportedBBCodes()));
    activity.setUserId(author.getId());
    activity.setTitle(ForumTransformHTML.getTitleInHTMLCode(title));
    activity.setBody(body);
    activity.setType(FORUM_APP_ID);
    templateParams.put(FORUM_ID_KEY, forumId);
    templateParams.put(CATE_ID_KEY, categoryId);
    templateParams.put(TOPIC_ID_KEY, topicId);
    templateParams.put(ACTIVITY_TYPE_KEY, type);
    activity.setTemplateParams(templateParams);
    return activity;
  }

  private Map<String, String> updateTemplateParams(Map<String, String> templateParams, String id, String link, String owner, String name, ACTIVITYTYPE type) throws Exception {
    if (type.name().indexOf(POST_TYPE) > 0) {
      templateParams.put(POST_ID_KEY, id);
      templateParams.put(POST_LINK_KEY, link);
      templateParams.put(POST_NAME_KEY, ForumTransformHTML.getTitleInHTMLCode(name));
      templateParams.put(POST_OWNER_KEY, owner);
    } else {
      templateParams.put(TOPIC_ID_KEY, id);
      templateParams.put(TOPIC_LINK_KEY, link);
      templateParams.put(TOPIC_NAME_KEY, ForumTransformHTML.getTitleInHTMLCode(name));
      templateParams.put(TOPIC_OWNER_KEY, owner);
    }
    return templateParams;
  }
  
  private boolean isCategoryPublic(Category category) {
    // the category is public when it does not restrict viewers and private users.
    return category != null && Utils.isEmpty(category.getViewer()) && Utils.isEmpty(category.getUserPrivate());
  }
 
  private boolean isForumPublic(Forum forum) {
 // the forum is public when it does not restrict viewers and is opening.
    return forum != null && !forum.getIsClosed() && Utils.isEmpty(forum.getViewer());
  }
  
  private boolean isTopicPublic(Topic topic) {
    // the topic is public when it is active, not waiting, not closed yet and does not restrict users
    return topic != null && topic.getIsActive() && topic.getIsApproved() && !topic.getIsWaiting() && !topic.getIsClosed() && Utils.isEmpty(topic.getCanView());
  }
  
  private boolean isPostPublic(Post post) {
    // the post is public when it is not private, not hidden by censored words, active by topic and not waiting for approval
    return post != null && post.getUserPrivate().length != TYPE_PRIVATE && !post.getIsWaiting() && !post.getIsHidden() && post.getIsActiveByTopic() && post.getIsApproved();
  }
  

  private boolean hasSpace(String forumId) throws Exception {
    return !Utils.isEmpty(forumId) && forumId.indexOf(Utils.FORUM_SPACE_ID_PREFIX) >= 0;
  }
  
  private Identity getSpaceIdentity(String forumId) {
    IdentityManager identityM = (IdentityManager) ExoContainerContext.getCurrentContainer().getComponentInstanceOfType(IdentityManager.class);
    SpaceService spaceS = (SpaceService) ExoContainerContext.getCurrentContainer().getComponentInstanceOfType(SpaceService.class);
    String prettyname = forumId.replaceFirst(Utils.FORUM_SPACE_ID_PREFIX, "");
    Space space = spaceS.getSpaceByPrettyName(prettyname);
    Identity spaceIdentity = null;
    if (space != null) {
      spaceIdentity = identityM.getOrCreateIdentity(SpaceIdentityProvider.NAME, space.getPrettyName(), false);
    }
    return spaceIdentity;
  }
  
  private void saveActivityForPost(Post post, String categoryId, String forumId, String topicId, ACTIVITYTYPE type) {
      ForumService forumService = (ForumService) ExoContainerContext.getCurrentContainer().getComponentInstanceOfType(ForumService.class);
      IdentityManager identityM = (IdentityManager) ExoContainerContext.getCurrentContainer().getComponentInstanceOfType(IdentityManager.class);
      if (isPostPublic(post)) {
        try {
        Identity userIdentity = identityM.getOrCreateIdentity(OrganizationIdentityProvider.NAME, ConversationState.getCurrent().getIdentity().getUserId(), false);
        Identity ownerStream = null, author = userIdentity;
        Topic topic = forumService.getTopic(categoryId, forumId, topicId, "");
        if (isTopicPublic(topic)) {
          if (hasSpace(forumId)) {
            // publish the activity in the space stream. 
            ownerStream = getSpaceIdentity(forumId);
          }
          if (ownerStream == null && isCategoryPublic(forumService.getCategory(categoryId)) && isForumPublic(forumService.getForum(categoryId, forumId))) {
            ownerStream = userIdentity;
          }
          if (ownerStream != null) {
            Map<String, String> templateParams = updateTemplateParams(new HashMap<String, String>(), post.getId(), post.getLink(), post.getOwner(), post.getName(), type);
            saveActivity(ownerStream, activity(author, post.getName(), post.getMessage(), forumId, categoryId, topicId, type.name(), templateParams));
          }
        }
        } catch (Exception e) {
          LOG.error("Can not record Activity for space when post " + post.getId(), e);
        }
      }
      
  }
  
  private void saveActivityForTopic(Topic topic, String categoryId, String forumId, ACTIVITYTYPE type) {
    ForumService forumService = (ForumService) ExoContainerContext.getCurrentContainer().getComponentInstanceOfType(ForumService.class);
    IdentityManager identityM = (IdentityManager) ExoContainerContext.getCurrentContainer().getComponentInstanceOfType(IdentityManager.class);
    Identity userIdentity = identityM.getOrCreateIdentity(OrganizationIdentityProvider.NAME, ConversationState.getCurrent().getIdentity().getUserId(), false);
    Identity ownerStream = null, author;
    author = userIdentity;
    if (isTopicPublic(topic)) {
      try {
        if (hasSpace(forumId)) {
          ownerStream = getSpaceIdentity(forumId);
        }
        if (ownerStream == null && isCategoryPublic(forumService.getCategory(categoryId)) && isForumPublic(forumService.getForum(categoryId, forumId))) {
          // if the category and the forum are public, publishing the activity on the user stream.
          ownerStream = userIdentity;
        }
        if (ownerStream != null) {
          Map<String, String> templateParams = updateTemplateParams(new HashMap<String, String>(), topic.getId(), topic.getLink(), topic.getOwner(), topic.getTopicName(), type);
          saveActivity(ownerStream, activity(author, topic.getTopicName(), topic.getDescription(), forumId, categoryId, topic.getId(), type.name(), templateParams));
        }
      } catch (Exception e) {
        LOG.error("Can not record Activity for space when add topic " + e.getMessage());
      }
    }
  }
  
  @Override
  public void addPost(Post post, String categoryId, String forumId, String topicId) {
    saveActivityForPost(post, categoryId, forumId, topicId, ACTIVITYTYPE.AddPost) ;
  }

  @Override
  public void addTopic(Topic topic, String categoryId, String forumId) {
    saveActivityForTopic(topic, categoryId, forumId, ACTIVITYTYPE.AddTopic);
  }

  @Override
  public void updatePost(Post post, String categoryId, String forumId, String topicId) {
    saveActivityForPost(post, categoryId, forumId, topicId, ACTIVITYTYPE.UpdatePost);
  }

  @Override
  public void updateTopic(Topic topic, String categoryId, String forumId) {
    saveActivityForTopic(topic, categoryId, forumId, ACTIVITYTYPE.UpdateTopic);
  }
}
