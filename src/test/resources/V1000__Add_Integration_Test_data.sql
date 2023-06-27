--
-- PostgreSQL database dump
--

-- Dumped from database version 12.9 (Ubuntu 12.9-0ubuntu0.20.04.1)
-- Dumped by pg_dump version 12.9 (Ubuntu 12.9-0ubuntu0.20.04.1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Data for Name: organizations; Type: TABLE DATA; Schema: ${flyway:defaultSchema}; Owner: integadmin
--

COPY ${flyway:defaultSchema}.organizations (id, name, url, created_at, updated_at) FROM stdin;
3a054e6a-220d-4d49-8cbd-25447dfaa8ed	DataKaveri	datakaveri.org	2021-09-21 06:40:50.499548	2021-09-21 06:40:50.499548
\.


--
-- Data for Name: users; Type: TABLE DATA; Schema: ${flyway:defaultSchema}; Owner: integadmin
--

COPY ${flyway:defaultSchema}.users (id, phone, organization_id, email_hash, keycloak_id, created_at, updated_at) FROM stdin;
b2a705bd-9543-4dce-bbce-f3828e2de1d2	0000000000	3a054e6a-220d-4d49-8cbd-25447dfaa8ed	datakaveri.org/5cd4375c42a26ad2e13f003f714e90b91b623fbf	b2a705bd-9543-4dce-bbce-f3828e2de1d2	2021-09-21 06:55:23.905149	2021-09-21 06:55:23.905149
7b6fb9c0-8524-459e-afc3-df665a83cd16	0000000000	3a054e6a-220d-4d49-8cbd-25447dfaa8ed	datakaveri.org/9c13a2308bb0919bf146be8681eb886922d29282	7b6fb9c0-8524-459e-afc3-df665a83cd16	2021-09-21 07:42:20.818606	2021-09-21 07:42:20.818606
746442f5-18a7-44fd-8c8f-3e39e5026fae	0000000000	3a054e6a-220d-4d49-8cbd-25447dfaa8ed	datakaveri.org/0808eb81ea0e5773187ae06110f55915a55f5c05	746442f5-18a7-44fd-8c8f-3e39e5026fae	2021-09-21 08:43:22.021489	2021-09-21 08:43:22.021489
da00dc18-9f0e-40ea-808b-bd8eac11bccc	0000000000	3a054e6a-220d-4d49-8cbd-25447dfaa8ed	datakaveri.org/46633d434665bd3defda2e24e8de57fc8b5d4cba	da00dc18-9f0e-40ea-808b-bd8eac11bccc	2021-09-27 12:09:31.269865	2021-09-27 12:09:31.269865
1d086d89-db81-4959-ae5b-a760ef5c15fb	0000000000	3a054e6a-220d-4d49-8cbd-25447dfaa8ed	datakaveri.org/4e09402696536d7f6e5a7f8316f2dea65a6c4366	1d086d89-db81-4959-ae5b-a760ef5c15fb	2022-03-21 06:25:10.36597	2022-03-21 06:25:10.36597
\.


--
-- Data for Name: access_requests; Type: TABLE DATA; Schema: ${flyway:defaultSchema}; Owner: integadmin
--

COPY ${flyway:defaultSchema}.access_requests (id, user_id, item_id, item_type, owner_id, status, expiry_duration, constraints, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: apds; Type: TABLE DATA; Schema: ${flyway:defaultSchema}; Owner: integadmin
--

COPY ${flyway:defaultSchema}.apds (id, name, url, status, created_at, updated_at) FROM stdin;
4f51cee5-e6ce-4e31-8c30-66d298c7d4a6	Active Integration APD	activeapd.integration-iudx.io	ACTIVE	2022-03-21 07:47:25.098821	2022-03-21 08:29:34.802582
1b988be6-cc13-422b-bca0-9ccb98a5b30f	Inactive Integration APD	inactiveapd.integration-iudx.io	INACTIVE	2022-03-21 07:49:24.904527	2022-03-21 08:30:12.182393
\.


--
-- Data for Name: apd_policies; Type: TABLE DATA; Schema: ${flyway:defaultSchema}; Owner: integadmin
--

COPY ${flyway:defaultSchema}.apd_policies (id, apd_id, user_class, item_id, item_type, owner_id, status, expiry_time, constraints, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: policies; Type: TABLE DATA; Schema: ${flyway:defaultSchema}; Owner: integadmin
--

COPY ${flyway:defaultSchema}.policies (id, user_id, item_id, item_type, owner_id, status, expiry_time, constraints, created_at, updated_at) FROM stdin;
5b412c34-ba88-4cd8-972b-d4c1138a9f26	746442f5-18a7-44fd-8c8f-3e39e5026fae	dc1f78c4-8f3f-467a-9f0f-b2218beba7ef	RESOURCE_SERVER	b2a705bd-9543-4dce-bbce-f3828e2de1d2	ACTIVE	2026-09-22 08:09:28.19591	{}	2021-09-22 08:09:28.170447	2021-09-22 08:09:28.170447
c24d4fe5-e24f-467a-b48f-238713f143d8	da00dc18-9f0e-40ea-808b-bd8eac11bccc	dc1f78c4-8f3f-467a-9f0f-b2218beba7ef	RESOURCE_SERVER	b2a705bd-9543-4dce-bbce-f3828e2de1d2	ACTIVE	2026-09-27 12:29:57.313555	{}	2021-09-27 12:29:57.272142	2021-09-27 12:29:57.272142
48a8e298-33e7-40a5-b6fb-14623063488f	1d086d89-db81-4959-ae5b-a760ef5c15fb	dc1f78c4-8f3f-467a-9f0f-b2218beba7ef	RESOURCE_SERVER	b2a705bd-9543-4dce-bbce-f3828e2de1d2	ACTIVE	2027-03-21 08:29:35.129844	{}	2022-03-21 08:29:35.247749	2022-03-21 08:29:35.247749
\.


--
-- Data for Name: approved_access_requests; Type: TABLE DATA; Schema: ${flyway:defaultSchema}; Owner: integadmin
--

COPY ${flyway:defaultSchema}.approved_access_requests (id, request_id, policy_id, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: resource_server; Type: TABLE DATA; Schema: ${flyway:defaultSchema}; Owner: integadmin
--

COPY ${flyway:defaultSchema}.resource_server (id, name, owner_id, url, created_at, updated_at) FROM stdin;
ff2db8e1-c36c-463f-aa97-626e76715593	Dev catalogue	7b6fb9c0-8524-459e-afc3-df665a83cd16	cat-test.iudx.io	2021-09-21 07:56:25.79898	2021-09-21 07:56:25.79898
ab7785ed-9611-4e67-87fd-be1e5797ab84	Dev RS	7b6fb9c0-8524-459e-afc3-df665a83cd16	rs.iudx.io	2021-09-21 07:56:40.2378	2021-09-21 07:56:40.2378
dc1f78c4-8f3f-467a-9f0f-b2218beba7ef	Dev Auth	b2a705bd-9543-4dce-bbce-f3828e2de1d2	authorization.iudx.io	2021-09-21 07:58:49.236819	2021-09-21 07:58:49.236819
\.


--
-- Data for Name: delegations; Type: TABLE DATA; Schema: ${flyway:defaultSchema}; Owner: integadmin
--

COPY ${flyway:defaultSchema}.delegations (id, owner_id, user_id, resource_server_id, status, created_at, updated_at) FROM stdin;
d3c388c7-5364-46a4-ac06-b0f90f005e05	746442f5-18a7-44fd-8c8f-3e39e5026fae	da00dc18-9f0e-40ea-808b-bd8eac11bccc	dc1f78c4-8f3f-467a-9f0f-b2218beba7ef	ACTIVE	2021-09-27 12:35:10.257781	2021-09-27 12:35:10.257781
\.


--
-- Data for Name: resource_group; Type: TABLE DATA; Schema: ${flyway:defaultSchema}; Owner: integadmin
--

COPY ${flyway:defaultSchema}.resource_group (id, cat_id, provider_id, resource_server_id, created_at, updated_at) FROM stdin;
4b367af2-ad55-4017-9e19-35a5fa37e9b8	datakaveri.org/0808eb81ea0e5773187ae06110f55915a55f5c05/rs.iudx.io/integration-test-rsg-one	746442f5-18a7-44fd-8c8f-3e39e5026fae	ab7785ed-9611-4e67-87fd-be1e5797ab84	2021-09-22 05:24:18.991299	2021-09-22 05:24:18.991299
aec83a80-61ec-4ae0-8671-80194f2ce73e	datakaveri.org/0808eb81ea0e5773187ae06110f55915a55f5c05/rs.iudx.io/integration-test-rsg-two	746442f5-18a7-44fd-8c8f-3e39e5026fae	ab7785ed-9611-4e67-87fd-be1e5797ab84	2021-09-22 08:15:08.297981	2021-09-22 08:15:08.297981
decffa8a-a1ec-3a60-e571-cafedead5351	datakaveri.org/9c13a2308bb0919bf146be8681eb886922d29282/rs.iudx.io/invalid-resource-for-ownership-test	7b6fb9c0-8524-459e-afc3-df665a83cd16	ab7785ed-9611-4e67-87fd-be1e5797ab84	2021-09-22 08:15:08.297981	2021-09-22 08:15:08.297981
\.


--
-- Data for Name: resource; Type: TABLE DATA; Schema: ${flyway:defaultSchema}; Owner: integadmin
--

COPY ${flyway:defaultSchema}.resource (id, cat_id, provider_id, resource_group_id, created_at, updated_at, resource_server_id) FROM stdin;
8eed51c0-bc79-4cdd-af72-521e8dd37020	datakaveri.org/0808eb81ea0e5773187ae06110f55915a55f5c05/rs.iudx.io/integration-test-rsg-one/test-resource-one	746442f5-18a7-44fd-8c8f-3e39e5026fae	4b367af2-ad55-4017-9e19-35a5fa37e9b8	2021-09-22 05:34:38.777171	2021-09-22 05:34:38.777171	ab7785ed-9611-4e67-87fd-be1e5797ab84
2923c77f-741b-4c3a-b010-183efbd43dee	datakaveri.org/0808eb81ea0e5773187ae06110f55915a55f5c05/rs.iudx.io/integration-test-rsg-one/test-resource-two	746442f5-18a7-44fd-8c8f-3e39e5026fae	4b367af2-ad55-4017-9e19-35a5fa37e9b8	2021-09-22 05:34:44.529042	2021-09-22 05:34:44.529042	ab7785ed-9611-4e67-87fd-be1e5797ab84
7f333829-6ed7-4e9e-93d8-e12958f7a535	datakaveri.org/0808eb81ea0e5773187ae06110f55915a55f5c05/rs.iudx.io/integration-test-rsg-two/test-resource-one	746442f5-18a7-44fd-8c8f-3e39e5026fae	aec83a80-61ec-4ae0-8671-80194f2ce73e	2021-09-22 08:19:24.97452	2021-09-22 08:19:24.97452	ab7785ed-9611-4e67-87fd-be1e5797ab84
220c1911-3cdb-4012-bbcc-c5a223a12626	datakaveri.org/0808eb81ea0e5773187ae06110f55915a55f5c05/rs.iudx.io/integration-test-rsg-two/test-resource-two	746442f5-18a7-44fd-8c8f-3e39e5026fae	aec83a80-61ec-4ae0-8671-80194f2ce73e	2021-09-22 08:19:33.327619	2021-09-22 08:19:33.327619	ab7785ed-9611-4e67-87fd-be1e5797ab84
\.


--
-- Data for Name: roles; Type: TABLE DATA; Schema: ${flyway:defaultSchema}; Owner: integadmin
--

COPY ${flyway:defaultSchema}.roles (id, user_id, role, status, created_at, updated_at) FROM stdin;
abe00721-1871-4268-a70a-3a670d843fea	b2a705bd-9543-4dce-bbce-f3828e2de1d2	ADMIN	APPROVED	2021-09-21 07:33:37.361742	2021-09-21 07:33:37.361742
cc831cdd-c894-41cf-a48b-d72760ca4f77	7b6fb9c0-8524-459e-afc3-df665a83cd16	ADMIN	APPROVED	2021-09-21 07:44:14.694661	2021-09-21 07:44:14.694661
b856f9d9-5071-46d8-b6f7-467193f28759	746442f5-18a7-44fd-8c8f-3e39e5026fae	PROVIDER	APPROVED	2021-09-21 08:43:22.021489	2021-09-22 08:09:27.588546
25ccc6f6-fe1a-487f-b7c5-c323a7f8b83f	da00dc18-9f0e-40ea-808b-bd8eac11bccc	DELEGATE	APPROVED	2021-09-27 12:09:31.269865	2021-09-27 12:09:31.269865
\.


--
-- Data for Name: user_clients; Type: TABLE DATA; Schema: ${flyway:defaultSchema}; Owner: integadmin
--

COPY ${flyway:defaultSchema}.user_clients (id, user_id, client_id, client_secret, client_name, created_at, updated_at) FROM stdin;
90a9f0c3-6404-406d-8b18-025f189afdad	b2a705bd-9543-4dce-bbce-f3828e2de1d2	540ee512-5b7a-416d-86d6-b7468d25c3fa	$2y$12$9/kAeUX8.ZiJvUEfMyzYeOxcjhH1AtVlft9tAacBz/9Xhdw/ulKN.	default	2021-09-21 06:55:23.905149	2021-09-21 06:55:23.905149
4cff3ee2-b4a2-421e-8a80-8b4912542f1a	7b6fb9c0-8524-459e-afc3-df665a83cd16	aa21bf13-d15a-4e45-a690-df2db960c62c	$2y$12$EaSQLsKUDBL6dXZZ8xbawu6c7Be27K4LIA8gquLVcsA8sWyxIcjFO	default	2021-09-21 07:42:20.818606	2021-09-21 07:42:20.818606
f7c41921-11d1-4bd2-b4ad-326384efc0f1	746442f5-18a7-44fd-8c8f-3e39e5026fae	b10f625d-bdf7-489c-bb28-a4d0039086a4	$2y$12$FQ73wx.5ngYK97mDn7Hci.nlH6b/i/ztwhXjXTEn/gemofiU2qjs.	default	2021-09-21 08:43:22.021489	2021-09-21 08:43:22.021489
d0ea9606-8f2d-48be-9137-5130f0295a08	da00dc18-9f0e-40ea-808b-bd8eac11bccc	9fdf5bbd-7880-4cb0-b9c9-f14bbe8e1fab	$2y$12$u8WUbDCwX/C1roWBwWWueew336DiW2TY4kgVYxvGYVgivUIaPkRa6	default	2021-09-27 12:09:31.269865	2021-09-27 12:09:31.269865
0cd08f7b-7e8e-4179-badf-c9fcdd484d39	1d086d89-db81-4959-ae5b-a760ef5c15fb	8e4b97f7-3f3d-4c50-b347-374337b900b7	5f600cc52882f8a04b5e22022b31b6b9d7560522fbb281db1d791189029be50e5bac5b9af71332b65bca4521e5cd3699574ca6dd0b5aca073fb842d7ca50be0a	default	2022-03-21 06:25:10.36597	2022-03-21 06:25:10.36597
\.


--
-- PostgreSQL database dump complete
--

