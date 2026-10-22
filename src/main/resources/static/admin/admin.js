"use strict";
// Tokens stay in memory and disappear when this page is closed or refreshed.
let token = null, selectedItem = null;
const pages = {items: 0, customers: 0};
const totals = {items: 0, customers: 0};
const $ = id => document.getElementById(id);
function message(text, error = false) { $("status").textContent = text; $("status").classList.toggle("error", error); }
function guarded(action) { return async event => { if (event) event.preventDefault(); try { await action(event); } catch (error) { message(error.message, true); } }; }
async function api(path, options = {}) {
  const headers = {"Content-Type": "application/json", ...options.headers};
  if (token) headers.Authorization = `Bearer ${token}`;
  const response = await fetch(`/api/v1${path}`, {...options, headers});
  if (response.status === 204) return null;
  const body = await response.json();
  if (!response.ok) {
    if (response.status === 401) signOut();
    throw new Error(body.detail || `Request failed (${response.status}).`);
  }
  return body;
}
function node(tag, text, parent) { const element = document.createElement(tag); element.textContent = text; if (parent) parent.append(element); return element; }
function money(value, currency) {
  if (value === null || value === undefined) return "Unavailable";
  const digits = new Intl.NumberFormat("en", {style: "currency", currency}).resolvedOptions().maximumFractionDigits;
  return new Intl.NumberFormat("en", {style: "currency", currency}).format(value / 10 ** digits);
}
function signOut() {
  token = null; selectedItem = null; $("workspace").hidden = true; $("logout").hidden = true; $("login-panel").hidden = false;
  ["items", "customers", "history"].forEach(id => $(id).replaceChildren()); $("price").reset(); $("price").hidden = true;
}
$("login").addEventListener("submit", guarded(async () => {
  const form = $("login"); const values = Object.fromEntries(new FormData(form));
  const body = await api("/auth/login", {method: "POST", body: JSON.stringify(values)});
  token = body.accessToken; form.reset();
  try { await Promise.all([loadItems(), loadCustomers()]); } catch (error) { signOut(); throw error; }
  $("login-panel").hidden = true; $("workspace").hidden = false; $("logout").hidden = false; message("Signed in.");
}));
$("logout").addEventListener("click", guarded(async () => { try { await api("/auth/logout", {method: "POST"}); } finally { signOut(); message("Signed out."); } }));
function pager(key, body) { totals[key] = body.totalPages; $(`${key}-page`).textContent = body.totalPages ? `Page ${body.page + 1} of ${body.totalPages}` : "No results"; $(`${key}-prev`).disabled = body.page === 0; $(`${key}-next`).disabled = body.page + 1 >= body.totalPages; }
async function loadItems() {
  const q = new FormData($("item-search")).get("q");
  const body = await api(`/management/items?q=${encodeURIComponent(q)}&page=${pages.items}&size=10`);
  $("items").replaceChildren(); pager("items", body);
  for (const item of body.items) {
    const card = node("div", "", $("items")); card.className = "record";
    node("strong", item.name, card); node("p", [item.brand, item.category, item.gadgetType, item.model].filter(Boolean).join(" · "), card);
    const button = node("button", "Manage price", card);
    button.addEventListener("click", guarded(async () => { selectedItem = item; $("price").hidden = false; $("price-title").textContent = item.name; await loadPrice(); }));
  }
}
async function loadPrice() {
  if (!selectedItem) return;
  const currency = $("price").elements.currency.value.toUpperCase();
  const quote = await api(`/management/items/${selectedItem.id}/price-intel?currency=${encodeURIComponent(currency)}`);
  $("current-price").textContent = `${quote.source}: ${money(quote.priceMinor, quote.currency)}`;
  const reference = quote.source === "Price checker specified intel";
  const digits = new Intl.NumberFormat("en", {style: "currency", currency: quote.currency}).resolvedOptions().maximumFractionDigits;
  $("price").elements.price.value = reference ? quote.priceMinor / 10 ** digits : "";
  $("price").elements.notes.value = reference ? quote.notes || "" : "";
}
$("load-price").addEventListener("click", guarded(loadPrice));
$("price").addEventListener("submit", guarded(async () => {
  const values = Object.fromEntries(new FormData($("price")));
  await api(`/management/items/${selectedItem.id}/price-intel`, {method: "PUT", body: JSON.stringify(values)});
  await loadPrice(); message("Reference price saved.");
}));
$("delete-price").addEventListener("click", guarded(async () => {
  await api(`/management/items/${selectedItem.id}/price-intel?currency=${encodeURIComponent($("price").elements.currency.value)}`, {method: "DELETE"});
  await loadPrice(); message("Reference removed. Seller averages apply where available.");
}));
$("create-item").addEventListener("submit", guarded(async () => {
  const values = Object.fromEntries(new FormData($("create-item")));
  for (const key of Object.keys(values)) if (!values[key]) values[key] = null;
  if (values.category === "GADGET") { values.applianceType = null; values.size = null; values.features = null; }
  else { values.gadgetType = null; values.series = null; }
  await api("/management/items", {method: "POST", body: JSON.stringify(values)});
  pages.items = 0; await loadItems(); message("Catalog item saved.");
}));
async function loadCustomers() {
  const q = new FormData($("customer-search")).get("q");
  const body = await api(`/management/telegram/users?q=${encodeURIComponent(q)}&page=${pages.customers}&size=10`);
  $("customers").replaceChildren(); $("history").replaceChildren(); pager("customers", body);
  for (const profile of body.items) {
    const card = node("div", "", $("customers")); card.className = "record";
    node("strong", profile.realName || "Name not provided yet", card);
    node("p", `Telegram ID: ${profile.telegramId}${profile.username ? ` · @${profile.username}` : ""}`, card);
    node("p", `Telegram name: ${[profile.firstName, profile.lastName].filter(Boolean).join(" ")}`, card);
    if (profile.selection) showSelection(profile.selection, card); else node("p", "No current selection.", card);
    node("button", "View conversations", card).addEventListener("click", guarded(() => loadHistory(profile.telegramId, 0)));
  }
}
function showSelection(selection, parent) {
  node("p", `${selection.itemName} · ${selection.sellerName} · ${money(selection.priceMinor, selection.currency)}`, parent);
  node("p", [selection.model, selection.condition, selection.description].filter(Boolean).join(" · "), parent);
  node("p", `Listing ID: ${selection.id}`, parent);
}
async function loadHistory(telegramId, page) {
  const body = await api(`/management/telegram/users/${telegramId}/conversations?page=${page}&size=10`);
  $("history").replaceChildren(); node("h3", `Conversations · ${telegramId}`, $("history"));
  for (const conversation of body.items) {
    const card = node("div", "", $("history")); card.className = "record";
    node("strong", `${conversation.phase} · ${new Date(conversation.createdAt).toLocaleString()}`, card);
    node("p", [conversation.domain, conversation.gadgetType, conversation.question].filter(Boolean).join(" · "), card);
    if (conversation.selection) showSelection(conversation.selection, card);
  }
  if (page > 0) node("button", "Previous", $("history")).addEventListener("click", guarded(() => loadHistory(telegramId, page - 1)));
  if (page + 1 < body.totalPages) node("button", "Next", $("history")).addEventListener("click", guarded(() => loadHistory(telegramId, page + 1)));
}
for (const [key, loader, form] of [["items", loadItems, "item-search"], ["customers", loadCustomers, "customer-search"]]) {
  $(form).addEventListener("submit", guarded(async () => { pages[key] = 0; await loader(); }));
  $(`${key}-prev`).addEventListener("click", guarded(async () => { if (pages[key] > 0) { pages[key]--; await loader(); } }));
  $(`${key}-next`).addEventListener("click", guarded(async () => { if (pages[key] + 1 < totals[key]) { pages[key]++; await loader(); } }));
}
