package pt.unl.fct.di.apdc.trailblaze.resources;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import pt.unl.fct.di.apdc.trailblaze.util.*;

import com.google.cloud.datastore.*;

@Path("/login")
public class LoginResource {

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response login(LoginRequest request) {
		Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
		KeyFactory keyFactory = datastore.newKeyFactory().setKind("Account");
		String actualUsername = request.username;
		Key userKey = keyFactory.newKey(actualUsername);
		Entity user = datastore.get(userKey);

		if (user == null) {
			Query<Entity> q = Query.newEntityQueryBuilder()
					.setKind("Account")
					.setFilter(StructuredQuery.PropertyFilter.eq("email", request.username))
					.build();
			QueryResults<Entity> results = datastore.run(q);
			if (results.hasNext()) {
				user = results.next();
				actualUsername = user.getKey().getName();
			} else {
				return Response.status(Response.Status.UNAUTHORIZED).entity("Utilizador não existe.").build();
			}
		}

		if (!HashUtil.checkPassword(request.password, user.getString("password"))) {
			return Response.status(Response.Status.UNAUTHORIZED).entity("Password incorreta.").build();
		}

		if (!"ATIVADA".equals(user.getString("state"))) {
			return Response.status(Response.Status.FORBIDDEN).entity("Conta não está ativada.").build();
		}

		String token = java.util.UUID.randomUUID().toString();
		Key sessionKey = datastore.newKeyFactory().setKind("Session").newKey(token);
		Entity session = Entity.newBuilder(sessionKey)
                .set("username", actualUsername)
				.set("role", user.getString("role"))
				.set("creationTime", System.currentTimeMillis())
				.build();
		datastore.put(session);

		return Response.ok("{\"token\":\"" + token + "\"}").build();
	}
}
