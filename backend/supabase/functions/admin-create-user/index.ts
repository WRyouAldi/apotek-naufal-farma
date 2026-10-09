import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const ORG_ID = "a0b9d2f4-61c2-4c65-9eab-000000000001";
const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json",
};

function respond(status: number, body: Record<string, unknown>) {
  return new Response(JSON.stringify(body), { status, headers: corsHeaders });
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return respond(405, { error: "Method not allowed" });

  const url = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !anonKey || !serviceKey) return respond(500, { error: "Server is not configured" });

  const authorization = req.headers.get("Authorization");
  if (!authorization?.startsWith("Bearer ")) return respond(401, { error: "Authentication required" });

  const callerClient = createClient(url, anonKey, {
    global: { headers: { Authorization: authorization } },
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const adminClient = createClient(url, serviceKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  const { data: authData, error: authError } = await callerClient.auth.getUser();
  if (authError || !authData.user) return respond(401, { error: "Invalid session" });

  const { data: caller, error: profileError } = await adminClient
    .from("profiles")
    .select("user_id, organization_id, branch_id, role, is_active")
    .eq("user_id", authData.user.id)
    .maybeSingle();

  if (profileError || !caller?.is_active || caller.organization_id !== ORG_ID) {
    return respond(403, { error: "Active organization profile required" });
  }
  if (!["owner", "branch_admin"].includes(caller.role)) {
    return respond(403, { error: "Only owner or branch admin can invite users" });
  }

  let body: { email?: string; display_name?: string; role?: string; branch_id?: string | null };
  try { body = await req.json(); } catch { return respond(400, { error: "Invalid JSON body" }); }

  const email = String(body.email ?? "").trim().toLowerCase();
  const displayName = String(body.display_name ?? "").trim();
  const role = String(body.role ?? "");
  let branchId = body.branch_id ?? null;

  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return respond(400, { error: "Valid email is required" });
  if (!displayName || displayName.length > 100) return respond(400, { error: "Display name is required (max 100 characters)" });
  if (!["branch_admin", "cashier", "inventory", "owner"].includes(role)) return respond(400, { error: "Unsupported role" });

  if (caller.role === "branch_admin") {
    if (role === "owner" || role === "branch_admin") return respond(403, { error: "Branch admin cannot create owner/admin accounts" });
    branchId = caller.branch_id;
  } else if (role === "owner") {
    branchId = null;
  } else if (!branchId) {
    return respond(400, { error: "A branch is required for this role" });
  }

  if (role !== "owner") {
    const { data: branch, error: branchError } = await adminClient
      .from("branches").select("id")
      .eq("id", branchId).eq("organization_id", ORG_ID).eq("is_active", true).maybeSingle();
    if (branchError || !branch) return respond(400, { error: "Active branch not found" });
  }

  const { data: invited, error: inviteError } = await adminClient.auth.admin.inviteUserByEmail(email, {
    data: { display_name: displayName },
  });
  if (inviteError || !invited.user) return respond(400, { error: inviteError?.message ?? "Invitation failed" });

  const { error: insertError } = await adminClient.from("profiles").insert({
    user_id: invited.user.id,
    organization_id: ORG_ID,
    branch_id: branchId,
    role,
    display_name: displayName,
    is_active: true,
  });

  if (insertError) {
    await adminClient.auth.admin.deleteUser(invited.user.id);
    return respond(500, { error: "Profile creation failed; invitation was rolled back" });
  }

  await adminClient.from("audit_events").insert({
    organization_id: ORG_ID,
    branch_id: branchId,
    actor_user_id: authData.user.id,
    event_type: "user.invited",
    entity_type: "profile",
    entity_id: invited.user.id,
    details: { role, email, display_name: displayName },
  });

  return respond(201, { invited: true, user_id: invited.user.id, email, role, branch_id: branchId });
});
