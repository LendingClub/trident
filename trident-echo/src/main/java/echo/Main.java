package echo;

import static spark.Spark.*;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class Main {

	static ObjectMapper mapper = new ObjectMapper();

	public static void main(String[] args) {
        String path = System.getenv("path");
        if (path==null || path.length()==0) {
            path = "/*";
        }
		get(path, (req, res) -> {
			res.header("content/type", "application/json");
			ObjectNode r = new ObjectMapper().createObjectNode();
			r.put("app", System.getenv("app"));
			r.put("host", InetAddress.getLocalHost().getHostName());
			r.put("address", InetAddress.getLocalHost().getHostAddress());

			ObjectNode n = mapper.createObjectNode();
			n.put("requestMethod", req.requestMethod());
			n.put("pathInfo", req.pathInfo());
			n.put("contextPath", req.contextPath());
			n.put("host", req.host());
			n.put("servletPath", req.servletPath());
			n.put("userAgent", req.userAgent());
			n.put("body", req.body());
			n.put("contentType", req.contentType());
			n.put("ip", req.ip());
			n.put("port", req.port());
			n.put("protocol", req.protocol());
			n.put("queryString", req.queryString());

			ObjectNode headers = mapper.createObjectNode();
			req.headers().forEach(it -> {
				headers.put(it, req.headers(it));
			});
			n.set("headers", headers);
			r.set("request", n);

			ObjectNode env = mapper.createObjectNode();
			r.set("env", env);
			
			List<String> keys = new ArrayList<>();
			System.getenv().keySet().forEach(it->{
				keys.add(it);
			});
			Collections.sort(keys);
			keys.forEach(key->{
				String val = System.getenv(key);
				if (key.toLowerCase().contains("password")) {
					env.put(key, "*******");
				} else {
					env.put(key, val);
				}
			});
			System.getenv().forEach((k, v) -> {
				
			});

			return new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(r);
		});
	}
}