package de.tecca.zyrox.protect.web;

public final class ZyroxProtectDashboardRenderer {

    public String render() {
        return """
                <style>
                    .protect-toolbar { display: flex; flex-wrap: wrap; gap: 8px; margin: 12px 0; align-items: center; }
                    .protect-toolbar input, .protect-toolbar select { width: auto; min-width: 140px; max-width: 190px; margin-top: 0; }
                    .protect-toolbar button { border: 1px solid #4a5568; border-radius: 6px; padding: 9px 11px; background: #151a20; color: #edf2f7; cursor: pointer; }
                    .protect-toolbar button:disabled { cursor: not-allowed; opacity: 0.45; }
                    .protect-badges { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 10px; }
                    .protect-badge { border: 1px solid #303846; border-radius: 999px; padding: 3px 8px; font-size: 12px; color: #dbeafe; background: #172033; }
                    .protect-list { display: grid; gap: 8px; margin-top: 12px; }
                    .protect-event { border: 1px solid #303846; border-radius: 8px; background: #12161c; overflow: hidden; }
                    .protect-event-row { width: 100%; display: grid; grid-template-columns: 158px 86px 104px minmax(120px, 1fr) minmax(120px, 1fr) 2fr; gap: 10px; align-items: center; border: 0; padding: 11px 12px; background: transparent; color: #edf2f7; text-align: left; cursor: pointer; }
                    .protect-event-row:hover { background: #1a2029; }
                    .protect-event-row span { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
                    .protect-severity { font-weight: 800; }
                    .protect-severity.CRITICAL { color: #feb2b2; }
                    .protect-severity.WARNING { color: #fbd38d; }
                    .protect-severity.INFO { color: #9ae6b4; }
                    .protect-detail { display: none; border-top: 1px solid #303846; padding: 12px; background: #0f1318; }
                    .protect-event.open .protect-detail { display: grid; gap: 12px; }
                    .protect-detail-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(210px, 1fr)); gap: 12px; }
                    .protect-detail-group { border: 1px solid #303846; border-radius: 8px; padding: 10px; background: #111827; }
                    .protect-detail-group h4 { margin: 0 0 8px; font-size: 13px; color: #cbd5e0; }
                    .protect-detail-group dl { display: grid; grid-template-columns: minmax(82px, auto) 1fr; gap: 6px 10px; margin: 0; font-size: 12px; }
                    .protect-detail-group dt { color: #a0aec0; }
                    .protect-detail-group dd { margin: 0; min-width: 0; overflow-wrap: anywhere; color: #edf2f7; }
                    .protect-pager { display: flex; justify-content: space-between; gap: 10px; align-items: center; margin-top: 12px; }
                    @media (max-width: 980px) {
                        .protect-event-row { grid-template-columns: 1fr 84px; }
                        .protect-event-row span:nth-child(3), .protect-event-row span:nth-child(5), .protect-event-row span:nth-child(6) { grid-column: 1 / -1; }
                    }
                </style>
                <div id="protect-root">
                    <p class="empty">Loading ZyroxProtect...</p>
                </div>
                <script>
                    (() => {
                        const root = document.getElementById('protect-root');
                        const state = {
                            events: [],
                            filters: new URLSearchParams(),
                            limit: 50,
                            offset: 0,
                            hasMore: false
                        };

                        function postHeaders(extra = {}) {
                            if (typeof window.zyroxDashboardPostHeaders === 'function') {
                                return window.zyroxDashboardPostHeaders(extra);
                            }
                            return extra;
                        }

                        async function api(endpoint, options = {}) {
                            const response = await fetch('/api/plugins/zyrox-protect/' + endpoint, options);
                            const text = await response.text();
                            try {
                                return { ok: response.ok, data: JSON.parse(text) };
                            } catch (error) {
                                return { ok: response.ok, data: { success: false, message: text } };
                            }
                        }

                        async function load() {
                            const result = await api('status');
                            if (!result.ok) {
                                root.innerHTML = '<p class="empty">ZyroxProtect status could not be loaded.</p>';
                                return;
                            }
                            if (!result.data.configured) {
                                root.innerHTML = '<p class="empty">ZyroxProtect password is not configured. Run /protect setup-password from the server console.</p>';
                                return;
                            }
                            if (!result.data.unlocked) {
                                renderLocked();
                                return;
                            }
                            renderUnlocked(result.data);
                            await search();
                        }

                        function renderLocked() {
                            root.innerHTML = '<h3>ZyroxProtect</h3><p class="muted">Audit dashboard is locked.</p>';
                            const form = document.createElement('form');
                            form.className = 'protect-toolbar';
                            const input = document.createElement('input');
                            input.type = 'password';
                            input.placeholder = 'Audit password';
                            input.required = true;
                            const button = document.createElement('button');
                            button.className = 'primary';
                            button.textContent = 'Unlock';
                            const output = document.createElement('pre');
                            form.append(input, button, output);
                            form.addEventListener('submit', async event => {
                                event.preventDefault();
                                const body = new URLSearchParams();
                                body.set('password', input.value);
                                input.value = '';
                                const result = await api('unlock', {
                                    method: 'POST',
                                    headers: postHeaders({ 'Content-Type': 'application/x-www-form-urlencoded' }),
                                    body
                                });
                                output.textContent = result.data.message || '';
                                if (result.data.success) await load();
                            });
                            root.appendChild(form);
                        }

                        function renderUnlocked(data) {
                            root.innerHTML = '';
                            const badges = document.createElement('div');
                            badges.className = 'protect-badges';
                            badges.innerHTML = '<span class="protect-badge">' + escapeHtml(data.hashMessage) + '</span><span class="protect-badge">' + Number(data.eventCount || 0) + ' events</span>';
                            root.appendChild(badges);

                            const filters = document.createElement('form');
                            filters.className = 'protect-toolbar';
                            filters.innerHTML = `
                                <select name="category"><option value="">Category</option><option>AUTH</option><option>AUDIT</option><option>COMMAND</option><option>LOGIN</option><option>LUCKPERMS</option><option>PACKET</option><option>PLUGIN</option><option>ZYROX_API</option></select>
                                <select name="severity"><option value="">Severity</option><option>INFO</option><option>WARNING</option><option>CRITICAL</option></select>
                                <input name="actor" placeholder="Actor">
                                <input name="target" placeholder="Target">
                                <input name="action" placeholder="Action">
                                <select name="limit"><option value="25">25</option><option value="50" selected>50</option><option value="100">100</option><option value="250">250</option><option value="500">500</option></select>
                                <button class="primary" type="submit">Search</button>
                                <button type="button" id="protect-lock">Lock</button>
                                <button type="button" id="protect-json">JSON</button>
                                <button type="button" id="protect-csv">CSV</button>
                            `;
                            root.appendChild(filters);

                            const list = document.createElement('div');
                            list.id = 'protect-events';
                            root.appendChild(list);

                            const pager = document.createElement('div');
                            pager.className = 'protect-pager';
                            pager.innerHTML = '<button type="button" id="protect-prev">Previous</button><span class="muted" id="protect-page"></span><button type="button" id="protect-next">Next</button>';
                            root.appendChild(pager);

                            filters.addEventListener('submit', async event => {
                                event.preventDefault();
                                state.offset = 0;
                                state.limit = parseInt(new FormData(filters).get('limit') || '50', 10);
                                state.filters = filtersFrom(new FormData(filters));
                                await search();
                            });
                            document.getElementById('protect-prev').addEventListener('click', async () => {
                                state.offset = Math.max(0, state.offset - state.limit);
                                await search();
                            });
                            document.getElementById('protect-next').addEventListener('click', async () => {
                                if (!state.hasMore) return;
                                state.offset += state.limit;
                                await search();
                            });
                            document.getElementById('protect-lock').addEventListener('click', async () => {
                                await api('lock', { method: 'POST', headers: postHeaders() });
                                await load();
                            });
                            document.getElementById('protect-json').addEventListener('click', () => exportData('json'));
                            document.getElementById('protect-csv').addEventListener('click', () => exportData('csv'));
                        }

                        function filtersFrom(formData) {
                            const params = new URLSearchParams();
                            for (const [key, value] of formData.entries()) {
                                if (key === 'limit') continue;
                                if (String(value).trim()) params.set(key, value);
                            }
                            return params;
                        }

                        function pageParams() {
                            const params = new URLSearchParams(state.filters);
                            params.set('limit', String(state.limit));
                            params.set('offset', String(state.offset));
                            return params;
                        }

                        async function search() {
                            const target = document.getElementById('protect-events');
                            if (target) target.innerHTML = '<p class="empty">Loading audit events...</p>';
                            const result = await api('events?' + pageParams().toString());
                            if (!result.ok || !result.data.success) {
                                if (target) target.innerHTML = '<p class="empty">Audit events could not be loaded.</p>';
                                return;
                            }
                            state.events = result.data.events || [];
                            state.hasMore = Boolean(result.data.hasMore);
                            renderEvents();
                        }

                        function renderEvents() {
                            const target = document.getElementById('protect-events');
                            const page = document.getElementById('protect-page');
                            const previous = document.getElementById('protect-prev');
                            const next = document.getElementById('protect-next');
                            if (!target) return;
                            target.innerHTML = '';
                            if (state.events.length === 0) {
                                target.innerHTML = '<p class="empty">No audit events match the current filters.</p>';
                            } else {
                                const list = document.createElement('div');
                                list.className = 'protect-list';
                                for (const event of state.events) {
                                    list.appendChild(eventItem(event));
                                }
                                target.appendChild(list);
                            }
                            if (page) page.textContent = 'Showing ' + (state.offset + 1) + '-' + (state.offset + state.events.length);
                            if (previous) previous.disabled = state.offset === 0;
                            if (next) next.disabled = !state.hasMore;
                        }

                        function eventItem(event) {
                            const item = document.createElement('article');
                            item.className = 'protect-event';
                            const row = document.createElement('button');
                            row.type = 'button';
                            row.className = 'protect-event-row';
                            row.innerHTML =
                                '<span>' + escapeHtml(event.createdAt) + '</span>' +
                                '<span class="protect-severity ' + escapeHtml(event.severity) + '">' + escapeHtml(event.severity) + '</span>' +
                                '<span>' + escapeHtml(event.category) + '</span>' +
                                '<span title="' + escapeHtml(event.action) + '">' + escapeHtml(event.action) + '</span>' +
                                '<span title="' + escapeHtml(event.actorName) + '">' + escapeHtml(event.actorName || event.actorSource) + '</span>' +
                                '<span title="' + escapeHtml(event.summary) + '">' + escapeHtml(event.summary || event.targetName) + '</span>';
                            const detail = document.createElement('div');
                            detail.className = 'protect-detail';
                            detail.appendChild(detailGrid(event));
                            row.addEventListener('click', () => item.classList.toggle('open'));
                            item.append(row, detail);
                            return item;
                        }

                        function detailGrid(event) {
                            const grid = document.createElement('div');
                            grid.className = 'protect-detail-grid';
                            grid.appendChild(group('Actor', {
                                Source: event.actorSource,
                                Name: event.actorName,
                                UUID: event.actorUuid
                            }));
                            grid.appendChild(group('Target', {
                                Type: event.targetType,
                                Name: event.targetName,
                                UUID: event.targetUuid
                            }));
                            grid.appendChild(group('Details', event.details || {}));
                            grid.appendChild(group('Integrity', {
                                'Previous hash': event.previousHash,
                                'Event hash': event.eventHash
                            }));
                            if (event.requestMeta) {
                                grid.appendChild(group('Request', { Meta: event.requestMeta }));
                            }
                            return grid;
                        }

                        function group(title, values) {
                            const section = document.createElement('section');
                            section.className = 'protect-detail-group';
                            const heading = document.createElement('h4');
                            heading.textContent = title;
                            const list = document.createElement('dl');
                            const entries = Object.entries(values).filter(([, value]) => String(value || '').trim());
                            if (entries.length === 0) {
                                entries.push(['-', 'No data']);
                            }
                            for (const [key, value] of entries) {
                                const term = document.createElement('dt');
                                term.textContent = key;
                                const description = document.createElement('dd');
                                description.textContent = String(value);
                                list.append(term, description);
                            }
                            section.append(heading, list);
                            return section;
                        }

                        async function exportData(format) {
                            const params = new URLSearchParams(state.filters);
                            params.set('format', format);
                            const response = await fetch('/api/plugins/zyrox-protect/export?' + params.toString());
                            const text = await response.text();
                            if (!response.ok) {
                                alert(text || 'Export failed.');
                                return;
                            }
                            const type = format === 'csv' ? 'text/csv;charset=utf-8' : 'application/json;charset=utf-8';
                            const blob = new Blob([text], { type });
                            const url = URL.createObjectURL(blob);
                            const link = document.createElement('a');
                            link.href = url;
                            link.download = 'audit-export.' + format;
                            document.body.appendChild(link);
                            link.click();
                            link.remove();
                            URL.revokeObjectURL(url);
                        }

                        function escapeHtml(value) {
                            return String(value || '').replace(/[&<>"']/g, character => ({
                                '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
                            }[character]));
                        }

                        load();
                    })();
                </script>
                """;
    }
}
