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
  const adminClient = createClient(url, serviceKey, { auth: { persistSession: false, autoRefreshToken: false } });
  const { data: authData, error: authError } = await callerClient.auth.getUser();
  if (authError || !authData.user) return respond(401, { error: "Invalid session" });

  const { data: profile, error: profileError } = await adminClient.from("profiles")
    .select("user_id, organization_id, role, is_active")
    .eq("user_id", authData.user.id).maybeSingle();
  if (profileError || !profile?.is_active || profile.organization_id !== ORG_ID || profile.role !== "owner") {
    return respond(403, { error: "Active organization owner required" });
  }

  let body: { code?: string; name?: string; address?: string };
  try { body = await req.json(); } catch { return respond(400, { error: "Invalid JSON body" }); }
  const code = String(body.code ?? "").trim().toUpperCase();
  const name = String(body.name ?? "").trim();
  const address = String(body.address ?? "").trim();
  if (!/^[A-Z0-9_-]{2,20}$/.test(code)) return respond(400, { error: "Branch code must be 2-20 letters/numbers/_/-" });
  if (!name || name.length > 120) return respond(400, { error: "Branch name is required (max 120 characters)" });
  if (address.length > 300) return respond(400, { error: "Address is too long" });

  const { data: branch, error } = await adminClient.from("branches").insert({
    organization_id: ORG_ID, code, name, address: address || null, is_active: true,
  }).select("id, code, name, address, is_active").single();
  if (error || !branch) return respond(400, { error: error?.message ?? "Branch creation failed" });

  await adminClient.from("audit_events").insert({
    organization_id: ORG_ID,
    actor_user_id: authData.user.id,
    event_type: "branch.created",
    entity_type: "branch",
    entity_id: branch.id,
    details: { code, name },
  });
  return respond(201, { branch });
});
