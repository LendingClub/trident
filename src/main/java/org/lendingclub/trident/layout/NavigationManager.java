package org.lendingclub.trident.layout;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.ws.rs.core.SecurityContext;

import org.lendingclub.trident.layout.NavigationManager.MenuItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;

public class NavigationManager {

	static int DEFAULT_ORDER =100;
	Logger logger = LoggerFactory.getLogger(NavigationManager.class);
	List<SidebarDecorator> sidebarDecorators = Lists.newCopyOnWriteArrayList();

	Cache<String, MenuItem> sidebarCache = CacheBuilder.newBuilder().expireAfterAccess(5, TimeUnit.MINUTES).maximumSize(500).build();
	public interface SidebarDecorator {
		public void decorate(MenuItem root);
	}

	public class MenuItem implements Comparable {
	
		int ordering = DEFAULT_ORDER;
		String label;
		String link;
		String fontAwesomeClass;
		String id;
		List<MenuItem> childItems = Lists.newArrayList();

		public MenuItem withOrder(int order) {
			this.ordering = order;
			return this;
		}

		public MenuItem withLabel(String text) {
			this.label = text;
			this.id = toId(text);
			return this;
		}

		public MenuItem withLink(String link) {
			this.link = link;
			return this;
		}

		public MenuItem add(MenuItem item) {
			childItems.add(item);
			return this;
		}

		public MenuItem withFontAwesomeClass(String faClass) {
			this.fontAwesomeClass = faClass;
			return this;
		}

		public String getLabel() {
			return label;
		}

		public String getLink() {
			return link;
		}

		public String getFontAwesomeClass() {
			return fontAwesomeClass;
		}

		public List<MenuItem> getChildren() {
			return childItems;
		}

		public Optional<MenuItem> find(String text) {
			return find(this, text);
		}

		Optional<MenuItem> find(MenuItem node, String text) {
			
			if (Strings.nullToEmpty(node.id).equals(toId(text))) {
				return Optional.of(node);
			}
			for (MenuItem child : node.childItems) {
				Optional<MenuItem> result = find(child, text);
				if (result.isPresent()) {
					return result;
				}
			}
			return Optional.empty();
		}

		public int compareTo(Object obj) {

			MenuItem other = (MenuItem) obj;

			if (ordering > other.ordering) {
				return 1;
			} else if (ordering < other.ordering) {
				return -1;
			}

			return Strings.nullToEmpty(getLabel()).toLowerCase()
					.compareTo(Strings.nullToEmpty(other.getLabel()).toLowerCase());
		}
	}

	
	Optional<MenuItem> getSidebarForCurrentUser() {
		try {
			HttpServletRequest request = ServletRequestAttributes.class.cast(RequestContextHolder.getRequestAttributes()).getRequest();
			HttpSession session = request.getSession(true);
			MenuItem item = (MenuItem) session.getAttribute("sidebar");
			return Optional.ofNullable(item);
			
			

		} catch (RuntimeException e) {
			logger.warn("problem", e);
			return Optional.empty();
		}

	}

	MenuItem createSidebarForCurrentUser() {
		MenuItem root = new MenuItem().withLabel("root");

		MenuItem home = new MenuItem().withFontAwesomeClass("fa-home").withLabel("Home").withOrder(10);
		root.add(home);
		home.add(new MenuItem().withLink("/home").withLabel("Dashboard"));

		MenuItem platform = new MenuItem().withFontAwesomeClass("fa-share-alt").withLabel("Platform").withOrder(20);
		root.add(platform);

		MenuItem swarm = new MenuItem().withFontAwesomeClass("fa-sitemap").withLabel("Docker Swarm").withOrder(30);
		root.add(swarm);

		MenuItem misc = new MenuItem().withFontAwesomeClass("fa-cubes").withLabel("Misc").withOrder(40);
		root.add(misc);

		MenuItem admin = new MenuItem().withFontAwesomeClass("fa-gears").withLabel("Admin").withOrder(100);
		root.add(admin);

		// The rest of these should be moved to decorators

		platform = root.find("platform").get();
		platform.add(new MenuItem().withLink("/app-clusters").withLabel("App Clusters"));
		platform.add(new MenuItem().withLink("/swarm-discovery-search").withLabel("Discovery Search"));
		platform.add(new MenuItem().withLink("/envoy/instances").withLabel("Envoy"));
		platform.add(new MenuItem().withLink("/haproxy/instances").withLabel("HAProxy"));

		swarm = root.find("Docker Swarm").get();
		swarm.add(new MenuItem().withLink("/swarm-services").withLabel("Services"));
		swarm.add(new MenuItem().withLink("/swarm-clusters").withLabel("Swarms"));

		misc = root.find("Misc").get();
		misc.add(new MenuItem().withLink("/cli").withLabel("CLI"));

		admin.add(new MenuItem().withLabel("AWS Accounts").withLink("/aws-accounts"));
		admin.add(new MenuItem().withLabel("Scheduler Log").withLink("/scheduler-history"));
		admin.add(new MenuItem().withLabel("Event Log").withLink("/events"));
		admin.add(new MenuItem().withLabel("Task Schedule").withLink("/task-schedule"));
		admin.add(new MenuItem().withLabel("Settings").withLink("/settings"));

		for (SidebarDecorator decorator : sidebarDecorators) {
			decorator.decorate(root);
		}
		sort(root);
		return root;
	}

	private void sort(MenuItem item) {

		for (MenuItem child : item.getChildren()) {
			sort(child);
		}
		Collections.sort((List) item.childItems);
	}

	public MenuItem getSidebarMenu() {
		
	
		Optional<MenuItem> sidebar = getSidebarForCurrentUser();
		if (sidebar.isPresent()) {
			
			return sidebar.get();
		}

		MenuItem createdItem = createSidebarForCurrentUser();
		
		try {
			
			HttpServletRequest request = ServletRequestAttributes.class.cast(RequestContextHolder.getRequestAttributes()).getRequest();
			HttpSession session = request.getSession(true);
			
			session.setAttribute("sidebar", createdItem);
			
			

		} catch (RuntimeException e) {
			logger.warn("problem", e);
		}
		return createdItem;
	}

	static String toId(String text) {
		return Strings.nullToEmpty(text).replace(" ", "_").toLowerCase();
	}

	/**
	 * Convenience method to register a sidebar item.  Note that it is simply registering a decorator, which will be evaluated on a per-user bases when 
	 * the sidebar is actually constructed.
	 * @param parent
	 * @param label
	 * @param link
	 */
	public void addSidebarItemDecorator(String parent, String label, String link, int order) {
		SidebarDecorator decorator = new SidebarDecorator() {

			@Override
			public void decorate(MenuItem root) {
				Optional<MenuItem> mi = root.find(parent);
				if (mi.isPresent()) {
					// The following protects against duplicates
					if (!mi.get().find(label).isPresent()) {
						mi.get().add(new MenuItem().withLabel(label).withLink(link).withOrder(order));
					}
				}

			}
		};
		sidebarDecorators.add(decorator);
	}
	/**
	 * Convenience method to register a sidebar item.  Note that it is simply registering a decorator, which will be evaluated on a per-user bases when 
	 * the sidebar is actually constructed.
	 * @param parent
	 * @param label
	 * @param link
	 */
	public void addSidebarItemDecorator(String parent, String label, String link) {
		addSidebarItemDecorator(parent, label, link,DEFAULT_ORDER);
	}
}
