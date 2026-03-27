--
-- PostgreSQL database dump
--


-- Dumped from database version 15.16 (Debian 15.16-1.pgdg13+1)
-- Dumped by pg_dump version 15.16 (Debian 15.16-1.pgdg13+1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: ApprovalStatus; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."ApprovalStatus" AS ENUM (
    'APPROVED',
    'PENDING'
);


--
-- Name: Direction; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."Direction" AS ENUM (
    'Ascending',
    'Descending'
);


--
-- Name: GroupBy; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."GroupBy" AS ENUM (
    'dtstart',
    'due',
    'duration',
    'priority',
    'rrule',
    'project'
);


--
-- Name: Priority; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."Priority" AS ENUM (
    'Low',
    'Medium',
    'High'
);


--
-- Name: ProjectColor; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."ProjectColor" AS ENUM (
    'RED',
    'ORANGE',
    'YELLOW',
    'LIME',
    'BLUE',
    'PURPLE',
    'PINK',
    'TEAL',
    'CORAL',
    'GOLD',
    'DEEP_BLUE',
    'ROSE',
    'LIGHT_RED',
    'BRICK',
    'SLATE'
);


--
-- Name: SortBy; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."SortBy" AS ENUM (
    'dtstart',
    'due',
    'duration',
    'priority'
);


--
-- Name: UserRole; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public."UserRole" AS ENUM (
    'ADMIN',
    'USER'
);


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: User; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."User" (
    id character varying(30) NOT NULL,
    name character varying(255),
    email character varying(255) NOT NULL,
    "emailVerified" timestamp without time zone,
    password text,
    image text,
    "createdAt" timestamp without time zone NOT NULL,
    "updatedAt" timestamp without time zone NOT NULL,
    "maxStorage" numeric(20,1) DEFAULT 1000000.0 NOT NULL,
    "usedStoraged" numeric(20,1) DEFAULT 0.0 NOT NULL,
    "protectedSymmetricKey" text,
    "enableEncryption" boolean DEFAULT true NOT NULL,
    "timeZone" character varying(64),
    role public."UserRole" NOT NULL,
    "approvalStatus" public."ApprovalStatus" NOT NULL,
    "tokenVersion" integer DEFAULT 0 NOT NULL,
    "approvedAt" timestamp without time zone,
    "approvedById" character varying(30)
);


--
-- Name: account; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.account (
    "userId" character varying(30) NOT NULL,
    type character varying(64) NOT NULL,
    provider character varying(64) NOT NULL,
    "providerAccountId" character varying(255) NOT NULL,
    refresh_token text,
    access_token text,
    expires_at integer,
    token_type character varying(64),
    scope text,
    id_token text,
    session_state text,
    "createdAt" timestamp without time zone NOT NULL,
    "updatedAt" timestamp without time zone NOT NULL
);


--
-- Name: appconfig; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.appconfig (
    id integer NOT NULL,
    "aiSummaryEnabled" boolean DEFAULT true NOT NULL,
    "updatedById" character varying(30),
    "createdAt" timestamp without time zone NOT NULL,
    "updatedAt" timestamp without time zone NOT NULL
);


--
-- Name: authsignal; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.authsignal (
    id character varying(30) NOT NULL,
    "identifierHash" character varying(255) NOT NULL,
    "lastIpHash" character varying(255),
    "lastDeviceHash" character varying(255),
    "lastSeenAt" timestamp without time zone NOT NULL,
    "createdAt" timestamp without time zone NOT NULL,
    "updatedAt" timestamp without time zone NOT NULL
);


--
-- Name: auththrottle; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auththrottle (
    id character varying(30) NOT NULL,
    scope character varying(64) NOT NULL,
    "bucketKey" character varying(255) NOT NULL,
    "requestCount" integer DEFAULT 0 NOT NULL,
    "failureCount" integer DEFAULT 0 NOT NULL,
    "windowStart" timestamp without time zone NOT NULL,
    "lockUntil" timestamp without time zone,
    "lastFailureAt" timestamp without time zone,
    "createdAt" timestamp without time zone NOT NULL,
    "updatedAt" timestamp without time zone NOT NULL
);


--
-- Name: completedtodo; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.completedtodo (
    id character varying(30) NOT NULL,
    "originalTodoID" character varying(30) NOT NULL,
    title text NOT NULL,
    description text,
    priority public."Priority" NOT NULL,
    "completedAt" timestamp without time zone NOT NULL,
    dtstart timestamp without time zone NOT NULL,
    due timestamp without time zone NOT NULL,
    "completedOnTime" boolean NOT NULL,
    "daysToComplete" numeric(10,2) NOT NULL,
    rrule text,
    "userID" character varying(30) NOT NULL,
    "instanceDate" timestamp without time zone,
    "projectName" character varying(255),
    "projectColor" character varying(32)
);


--
-- Name: cronlog; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cronlog (
    id character varying(30) NOT NULL,
    "runAt" timestamp without time zone NOT NULL,
    success boolean NOT NULL,
    log text NOT NULL
);


--
-- Name: eventlog; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.eventlog (
    id character varying(30) NOT NULL,
    "capturedTime" timestamp without time zone NOT NULL,
    "eventName" text NOT NULL,
    log text NOT NULL
);


--
-- Name: file; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.file (
    id character varying(30) NOT NULL,
    name text NOT NULL,
    url text NOT NULL,
    size integer NOT NULL,
    "createdAt" timestamp without time zone NOT NULL,
    "userID" character varying(30) NOT NULL,
    "s3Key" text NOT NULL
);


--
-- Name: note; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.note (
    id character varying(30) NOT NULL,
    name text NOT NULL,
    content text,
    "createdAt" timestamp without time zone NOT NULL,
    "userID" character varying(30) NOT NULL
);


--
-- Name: project; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.project (
    id character varying(30) NOT NULL,
    name text NOT NULL,
    color public."ProjectColor",
    "iconKey" character varying(64),
    "userID" character varying(30) NOT NULL,
    "createdAt" timestamp without time zone NOT NULL,
    "updatedAt" timestamp without time zone NOT NULL
);


--
-- Name: todo_instances; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.todo_instances (
    id character varying(30) NOT NULL,
    "todoId" character varying(30) NOT NULL,
    "recurId" text NOT NULL,
    "instanceDate" timestamp without time zone NOT NULL,
    "overriddenTitle" text,
    "overriddenDescription" text,
    "overriddenPriority" public."Priority",
    "overriddenDtstart" timestamp without time zone,
    "overriddenDurationMinutes" integer,
    "overriddenDue" timestamp without time zone,
    "completedAt" timestamp without time zone
);


--
-- Name: todos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.todos (
    id character varying(30) NOT NULL,
    title text NOT NULL,
    description text,
    "createdAt" timestamp without time zone NOT NULL,
    "updatedAt" timestamp without time zone NOT NULL,
    "userID" character varying(30) NOT NULL,
    pinned boolean DEFAULT false NOT NULL,
    "order" integer NOT NULL,
    priority public."Priority" NOT NULL,
    dtstart timestamp without time zone NOT NULL,
    due timestamp without time zone NOT NULL,
    exdates timestamp(3) without time zone[] NOT NULL,
    "durationMinutes" integer DEFAULT 30 NOT NULL,
    rrule text,
    "timeZone" character varying(64) DEFAULT 'UTC'::character varying NOT NULL,
    completed boolean DEFAULT false NOT NULL,
    "projectID" character varying(30)
);


--
-- Name: todos_order_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.todos_order_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: todos_order_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.todos_order_seq OWNED BY public.todos."order";


--
-- Name: userpreferences; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.userpreferences (
    id character varying(30) NOT NULL,
    "userID" character varying(30) NOT NULL,
    "sortBy" public."SortBy",
    "groupBy" public."GroupBy",
    direction public."Direction"
);


--
-- Name: verificationtoken; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.verificationtoken (
    identifier character varying(255) NOT NULL,
    token text NOT NULL,
    expires timestamp without time zone NOT NULL
);


--
-- Name: todos order; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.todos ALTER COLUMN "order" SET DEFAULT nextval('public.todos_order_seq'::regclass);


--
-- Name: User User_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."User"
    ADD CONSTRAINT "User_pkey" PRIMARY KEY (id);


--
-- Name: appconfig appconfig_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appconfig
    ADD CONSTRAINT appconfig_pkey PRIMARY KEY (id);


--
-- Name: authsignal authsignal_identifierhash_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.authsignal
    ADD CONSTRAINT authsignal_identifierhash_unique UNIQUE ("identifierHash");


--
-- Name: authsignal authsignal_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.authsignal
    ADD CONSTRAINT authsignal_pkey PRIMARY KEY (id);


--
-- Name: auththrottle auththrottle_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auththrottle
    ADD CONSTRAINT auththrottle_pkey PRIMARY KEY (id);


--
-- Name: auththrottle auththrottle_scope_bucketkey_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auththrottle
    ADD CONSTRAINT auththrottle_scope_bucketkey_unique UNIQUE (scope, "bucketKey");


--
-- Name: completedtodo completedtodo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.completedtodo
    ADD CONSTRAINT completedtodo_pkey PRIMARY KEY (id);


--
-- Name: cronlog cronlog_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cronlog
    ADD CONSTRAINT cronlog_pkey PRIMARY KEY (id);


--
-- Name: eventlog eventlog_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.eventlog
    ADD CONSTRAINT eventlog_pkey PRIMARY KEY (id);


--
-- Name: file file_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.file
    ADD CONSTRAINT file_pkey PRIMARY KEY (id);


--
-- Name: note note_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.note
    ADD CONSTRAINT note_pkey PRIMARY KEY (id);


--
-- Name: account pk_account; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.account
    ADD CONSTRAINT pk_account PRIMARY KEY (provider, "providerAccountId");


--
-- Name: verificationtoken pk_verificationtoken; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.verificationtoken
    ADD CONSTRAINT pk_verificationtoken PRIMARY KEY (identifier, token);


--
-- Name: project project_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project
    ADD CONSTRAINT project_pkey PRIMARY KEY (id);


--
-- Name: todo_instances todo_instances_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.todo_instances
    ADD CONSTRAINT todo_instances_pkey PRIMARY KEY (id);


--
-- Name: todo_instances todo_instances_todoid_instancedate_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.todo_instances
    ADD CONSTRAINT todo_instances_todoid_instancedate_unique UNIQUE ("todoId", "instanceDate");


--
-- Name: todos todos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.todos
    ADD CONSTRAINT todos_pkey PRIMARY KEY (id);


--
-- Name: User user_email_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."User"
    ADD CONSTRAINT user_email_unique UNIQUE (email);


--
-- Name: userpreferences userpreferences_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.userpreferences
    ADD CONSTRAINT userpreferences_pkey PRIMARY KEY (id);


--
-- Name: userpreferences userpreferences_userid_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.userpreferences
    ADD CONSTRAINT userpreferences_userid_unique UNIQUE ("userID");


--
-- Name: appconfig_updatedbyid; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX appconfig_updatedbyid ON public.appconfig USING btree ("updatedById");


--
-- Name: authsignal_lastseenat; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX authsignal_lastseenat ON public.authsignal USING btree ("lastSeenAt");


--
-- Name: auththrottle_scope_lockuntil; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX auththrottle_scope_lockuntil ON public.auththrottle USING btree (scope, "lockUntil");


--
-- Name: completedtodo_userid; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX completedtodo_userid ON public.completedtodo USING btree ("userID");


--
-- Name: project_userid; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX project_userid ON public.project USING btree ("userID");


--
-- Name: todos_projectid; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX todos_projectid ON public.todos USING btree ("projectID");


--
-- Name: todos_userid; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX todos_userid ON public.todos USING btree ("userID");


--
-- Name: todos_userid_projectid; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX todos_userid_projectid ON public.todos USING btree ("userID", "projectID");


--
-- Name: account fk_account_userid__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.account
    ADD CONSTRAINT fk_account_userid__id FOREIGN KEY ("userId") REFERENCES public."User"(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: appconfig fk_appconfig_updatedbyid__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appconfig
    ADD CONSTRAINT fk_appconfig_updatedbyid__id FOREIGN KEY ("updatedById") REFERENCES public."User"(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: completedtodo fk_completedtodo_userid__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.completedtodo
    ADD CONSTRAINT fk_completedtodo_userid__id FOREIGN KEY ("userID") REFERENCES public."User"(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: file fk_file_userid__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.file
    ADD CONSTRAINT fk_file_userid__id FOREIGN KEY ("userID") REFERENCES public."User"(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: note fk_note_userid__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.note
    ADD CONSTRAINT fk_note_userid__id FOREIGN KEY ("userID") REFERENCES public."User"(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: project fk_project_userid__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project
    ADD CONSTRAINT fk_project_userid__id FOREIGN KEY ("userID") REFERENCES public."User"(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: todo_instances fk_todo_instances_todoid__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.todo_instances
    ADD CONSTRAINT fk_todo_instances_todoid__id FOREIGN KEY ("todoId") REFERENCES public.todos(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: todos fk_todos_projectid__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.todos
    ADD CONSTRAINT fk_todos_projectid__id FOREIGN KEY ("projectID") REFERENCES public.project(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: todos fk_todos_userid__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.todos
    ADD CONSTRAINT fk_todos_userid__id FOREIGN KEY ("userID") REFERENCES public."User"(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: User fk_user_approvedbyid__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."User"
    ADD CONSTRAINT fk_user_approvedbyid__id FOREIGN KEY ("approvedById") REFERENCES public."User"(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: userpreferences fk_userpreferences_userid__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.userpreferences
    ADD CONSTRAINT fk_userpreferences_userid__id FOREIGN KEY ("userID") REFERENCES public."User"(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- PostgreSQL database dump complete
--


