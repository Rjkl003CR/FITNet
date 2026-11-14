import { test, afterEach } from "node:test";
import assert from "node:assert/strict";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { parsePresenceUsers, fetchPresenceUsers } from "../../../.next/presence-tests/api.js";
import { PresenceList } from "../../../.next/presence-tests/PresenceList.js";
import { PresenceStatus } from "../../../.next/presence-tests/PresenceStatus.js";
const originalFetch = global.fetch;
const originalBase = process.env.NEXT_PUBLIC_PRESENCE_API_BASE_URL;
afterEach(() => {
  global.fetch = originalFetch;
  if (originalBase === undefined) delete process.env.NEXT_PUBLIC_PRESENCE_API_BASE_URL;
  else process.env.NEXT_PUBLIC_PRESENCE_API_BASE_URL = originalBase;
});
// Fixtures are test-only and never imported by the application.
const user = { userId: "student01", status: "ONLINE", lastSeen: "2026-11-14T10:00:00Z" };

test("accepts real-contract users, offline records, and an empty response", () => {
  assert.deepEqual(parsePresenceUsers([user]), [user]);
  assert.deepEqual(parsePresenceUsers([{ ...user, status: "OFFLINE" }])[0].status, "OFFLINE");
  assert.deepEqual(parsePresenceUsers([]), []);
});
test("rejects invalid shapes, statuses, timestamps and duplicate users", () => {
  for (const value of [null, {}, [null], [{ ...user, userId: "" }], [{ ...user, status: "AWAY" }],
    [{ ...user, lastSeen: "yesterday" }], [{ ...user, lastSeen: "2026-11-14" }], [user, user]]) {
    assert.throws(() => parsePresenceUsers(value));
  }
});
test("fetches the same-origin endpoint without cache and forwards cancellation", async () => {
  delete process.env.NEXT_PUBLIC_PRESENCE_API_BASE_URL;
  const controller = new AbortController();
  global.fetch = async (url, options) => {
    assert.equal(url, "/api/presence/users");
    assert.equal(options.cache, "no-store");
    assert.equal(options.signal, controller.signal);
    return Response.json([user]);
  };
  assert.deepEqual(await fetchPresenceUsers(controller.signal), [user]);
});
test("supports a configured Gateway origin", async () => {
  process.env.NEXT_PUBLIC_PRESENCE_API_BASE_URL = "http://localhost:8080/";
  global.fetch = async url => {
    assert.equal(url, "http://localhost:8080/api/presence/users");
    return Response.json([]);
  };
  assert.deepEqual(await fetchPresenceUsers(), []);
});
test("rejects HTTP failures, HTML fallback, bad JSON, and network failures", async () => {
  for (const response of [new Response("", { status: 404 }), new Response("<html>"),
    new Response("broken", { headers: { "content-type": "application/json" } }), Response.json({})]) {
    global.fetch = async () => response;
    await assert.rejects(fetchPresenceUsers());
  }
  global.fetch = async () => { throw new TypeError("Failed to fetch"); };
  await assert.rejects(fetchPresenceUsers());
});
test("propagates an aborted request for lifecycle cleanup", async () => {
  const controller = new AbortController();
  controller.abort();
  global.fetch = async (_, options) => { options.signal.throwIfAborted(); };
  await assert.rejects(fetchPresenceUsers(controller.signal), { name: "AbortError" });
});
test("renders both statuses as text, not just a color", () => {
  for (const status of ["ONLINE", "OFFLINE"]) {
    assert.match(renderToStaticMarkup(React.createElement(PresenceStatus, { status })), new RegExp(status));
  }
});
test("renders empty state without fabricated users", () => {
  const html = renderToStaticMarkup(React.createElement(PresenceList, { users: [] }));
  assert.match(html, /No users registered yet/);
  assert.doesNotMatch(html, /student01/);
});
test("renders IDs and last-seen dates, sorts online first, and preserves input", () => {
  const users = [{ ...user, userId: "offline-user", status: "OFFLINE" }, user];
  const html = renderToStaticMarkup(React.createElement(PresenceList, { users }));
  assert.ok(html.indexOf("student01") < html.indexOf("offline-user"));
  assert.match(html, /dateTime="2026-11-14T10:00:00Z"/);
  assert.equal(users[0].userId, "offline-user");
});
