/*
 * Copyright 2002-2005 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package info.jtrac.wicket;

import info.jtrac.domain.User;
import info.jtrac.domain.UserSpaceRole;
import java.util.ArrayList;
import java.util.List;
import wicket.behavior.SimpleAttributeModifier;
import wicket.markup.html.basic.Label;
import wicket.markup.html.link.Link;
import wicket.markup.html.list.ListItem;
import wicket.markup.html.list.ListView;
import wicket.model.PropertyModel;

/**
 * user management page
 */
public class UserListPage extends BasePage {
      
    public UserListPage() {
        
        super("User List");      
        
        add(new HeaderPanel(null));
        
        border.add(new Link("create") {
            public void onClick() {                
                setResponsePage(new UserFormPage());
            }            
        });
        
        List<User> users = getJtrac().findAllUsers();
        
        final SimpleAttributeModifier sam = new SimpleAttributeModifier("class", "alt");
        
        ListView listView = new ListView("users", users) {
            protected void populateItem(ListItem listItem) {
                final User user = (User) listItem.getModelObject();
                if(listItem.getIndex() % 2 == 1) {
                    listItem.add(sam);
                }                
                listItem.add(new Label("name", new PropertyModel(user, "name")));
                Link loginName = new Link("loginName") {
                    public void onClick() {
                        setResponsePage(new UserFormPage(user));
                    }                    
                };
                loginName.add(new Label("loginName", new PropertyModel(user, "loginName")));
                listItem.add(loginName);
                String locked = user.isLocked() ? "Y" : "";
                listItem.add(new Label("locked", locked));
                ListView spaceRoles = new ListView("spaceRoles", new ArrayList(user.getUserSpaceRoles())) {
                    protected void populateItem(ListItem item) {
                        UserSpaceRole usr = (UserSpaceRole) item.getModelObject();
                        item.add(new Link("space") {
                            public void onClick() {
                            }                            
                        }.add(new Label("space", new PropertyModel(usr, "space.prefixCode"))));
                        item.add(new Label("role", new PropertyModel(usr, "roleKey")));
                    }                    
                };
                listItem.add(spaceRoles);
                listItem.add(new Link("allocate") {
                    public void onClick() {
                    }                    
                });
            }            
        };
        
        border.add(listView);
        
    }
    
}