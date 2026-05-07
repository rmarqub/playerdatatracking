--
-- PostgreSQL database dump
--

-- Dumped from database version 16.3
-- Dumped by pg_dump version 16.3

-- Started on 2026-05-07 14:14:02

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
-- TOC entry 5 (class 2615 OID 2200)
-- Name: public; Type: SCHEMA; Schema: -; Owner: pg_database_owner
--

CREATE SCHEMA public;


ALTER SCHEMA public OWNER TO pg_database_owner;

--
-- TOC entry 5086 (class 0 OID 0)
-- Dependencies: 5
-- Name: SCHEMA public; Type: COMMENT; Schema: -; Owner: pg_database_owner
--

COMMENT ON SCHEMA public IS 'standard public schema';


--
-- TOC entry 300 (class 1255 OID 109495)
-- Name: j_int(text); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.j_int(x text) RETURNS integer
    LANGUAGE sql IMMUTABLE
    AS $_$
  SELECT NULLIF(regexp_replace($1, '[^0-9-]', '', 'g'), '')::int
$_$;


ALTER FUNCTION public.j_int(x text) OWNER TO postgres;

--
-- TOC entry 299 (class 1255 OID 109496)
-- Name: j_num(text); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.j_num(x text) RETURNS numeric
    LANGUAGE sql IMMUTABLE
    AS $_$
  SELECT CASE
           WHEN $1 ~ '^[0-9]+(\.[0-9]+)?$' THEN $1::numeric
           ELSE NULL
         END
$_$;


ALTER FUNCTION public.j_num(x text) OWNER TO postgres;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- TOC entry 223 (class 1259 OID 24622)
-- Name: api_football_keys; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.api_football_keys (
    id bigint NOT NULL,
    valor character varying(255) NOT NULL,
    last_used character varying(255),
    total_uses integer DEFAULT 0 NOT NULL,
    today_uses integer DEFAULT 0 NOT NULL,
    id_service integer NOT NULL,
    mail character varying(255) NOT NULL,
    ispermanentlyvalid boolean NOT NULL,
    is_valid boolean NOT NULL,
    hashkey character varying(255) NOT NULL
);


ALTER TABLE public.api_football_keys OWNER TO postgres;

--
-- TOC entry 222 (class 1259 OID 24621)
-- Name: api_football_keys_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.api_football_keys_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.api_football_keys_id_seq OWNER TO postgres;

--
-- TOC entry 5087 (class 0 OID 0)
-- Dependencies: 222
-- Name: api_football_keys_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.api_football_keys_id_seq OWNED BY public.api_football_keys.id;


--
-- TOC entry 225 (class 1259 OID 24632)
-- Name: api_services; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.api_services (
    id_service bigint NOT NULL,
    name character varying(255) NOT NULL
);


ALTER TABLE public.api_services OWNER TO postgres;

--
-- TOC entry 224 (class 1259 OID 24631)
-- Name: api_services_id_service_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.api_services_id_service_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.api_services_id_service_seq OWNER TO postgres;

--
-- TOC entry 5088 (class 0 OID 0)
-- Dependencies: 224
-- Name: api_services_id_service_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.api_services_id_service_seq OWNED BY public.api_services.id_service;


--
-- TOC entry 242 (class 1259 OID 82098)
-- Name: app_user; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.app_user (
    id bigint NOT NULL,
    username character varying(100) NOT NULL,
    password_hash character varying(100) NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.app_user OWNER TO postgres;

--
-- TOC entry 241 (class 1259 OID 82097)
-- Name: app_user_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.app_user_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.app_user_id_seq OWNER TO postgres;

--
-- TOC entry 5089 (class 0 OID 0)
-- Dependencies: 241
-- Name: app_user_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.app_user_id_seq OWNED BY public.app_user.id;


--
-- TOC entry 234 (class 1259 OID 49242)
-- Name: club; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.club (
    id bigint NOT NULL,
    pais integer,
    codeaf character varying(255),
    nombre character varying(255)
);


ALTER TABLE public.club OWNER TO postgres;

--
-- TOC entry 239 (class 1259 OID 81978)
-- Name: club_in_league; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.club_in_league (
    id bigint NOT NULL,
    club bigint NOT NULL,
    torneo bigint NOT NULL
);


ALTER TABLE public.club_in_league OWNER TO postgres;

--
-- TOC entry 238 (class 1259 OID 81977)
-- Name: club_in_league_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.club_in_league_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.club_in_league_id_seq OWNER TO postgres;

--
-- TOC entry 5090 (class 0 OID 0)
-- Dependencies: 238
-- Name: club_in_league_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.club_in_league_id_seq OWNED BY public.club_in_league.id;


--
-- TOC entry 237 (class 1259 OID 65582)
-- Name: config_params; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.config_params (
    id bigint NOT NULL,
    key character varying(255) NOT NULL,
    value character varying(255) NOT NULL
);


ALTER TABLE public.config_params OWNER TO postgres;

--
-- TOC entry 236 (class 1259 OID 65581)
-- Name: config_params_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.config_params_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.config_params_id_seq OWNER TO postgres;

--
-- TOC entry 5091 (class 0 OID 0)
-- Dependencies: 236
-- Name: config_params_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.config_params_id_seq OWNED BY public.config_params.id;


--
-- TOC entry 251 (class 1259 OID 172358)
-- Name: dupped_players; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.dupped_players (
    id bigint NOT NULL,
    season character varying(50) NOT NULL,
    player bigint NOT NULL,
    team bigint NOT NULL
);


ALTER TABLE public.dupped_players OWNER TO postgres;

--
-- TOC entry 250 (class 1259 OID 172357)
-- Name: dupped_players_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.dupped_players_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.dupped_players_id_seq OWNER TO postgres;

--
-- TOC entry 5092 (class 0 OID 0)
-- Dependencies: 250
-- Name: dupped_players_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.dupped_players_id_seq OWNED BY public.dupped_players.id;


--
-- TOC entry 252 (class 1259 OID 227837)
-- Name: fixture; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.fixture (
    id bigint NOT NULL,
    league_id integer NOT NULL,
    league_name character varying(255),
    season integer NOT NULL,
    round character varying(255),
    match_date timestamp with time zone NOT NULL,
    match_timestamp bigint,
    status_short character varying(10) NOT NULL,
    status_long character varying(255),
    status_elapsed integer,
    status_extra integer,
    referee character varying(255),
    venue_id integer,
    venue_name character varying(255),
    venue_city character varying(255),
    home_team_id bigint NOT NULL,
    home_team_name character varying(255),
    away_team_id bigint NOT NULL,
    away_team_name character varying(255),
    goals_home integer,
    goals_away integer,
    score_ht_home integer,
    score_ht_away integer,
    score_ft_home integer,
    score_ft_away integer,
    score_et_home integer,
    score_et_away integer,
    score_pen_home integer,
    score_pen_away integer,
    ingested_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    eventsstored boolean DEFAULT false,
    matchstored boolean DEFAULT false,
    lineupstored boolean DEFAULT false,
    statsstored boolean DEFAULT false,
    events_stored boolean,
    lineup_stored boolean,
    match_stored boolean,
    stats_stored boolean
);


ALTER TABLE public.fixture OWNER TO postgres;

--
-- TOC entry 253 (class 1259 OID 227862)
-- Name: fixture_event_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.fixture_event_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.fixture_event_id_seq OWNER TO postgres;

--
-- TOC entry 254 (class 1259 OID 227863)
-- Name: fixture_event; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.fixture_event (
    id bigint DEFAULT nextval('public.fixture_event_id_seq'::regclass) NOT NULL,
    fixture_id bigint NOT NULL,
    team_id bigint,
    time_elapsed integer,
    time_extra integer,
    player_id bigint,
    player_name character varying(255),
    assist_id bigint,
    assist_name character varying(255),
    event_type character varying(50) NOT NULL,
    event_detail character varying(100),
    comments character varying(255)
);


ALTER TABLE public.fixture_event OWNER TO postgres;

--
-- TOC entry 257 (class 1259 OID 228165)
-- Name: fixture_lineup_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.fixture_lineup_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.fixture_lineup_id_seq OWNER TO postgres;

--
-- TOC entry 258 (class 1259 OID 228166)
-- Name: fixture_lineup; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.fixture_lineup (
    id bigint DEFAULT nextval('public.fixture_lineup_id_seq'::regclass) NOT NULL,
    fixture_id bigint NOT NULL,
    team_id bigint NOT NULL,
    formation character varying(20),
    coach_id bigint,
    coach_name character varying(100),
    player_id bigint,
    player_name character varying(150),
    player_number integer,
    "position" character varying(10),
    grid character varying(10),
    substitute boolean DEFAULT false NOT NULL
);


ALTER TABLE public.fixture_lineup OWNER TO postgres;

--
-- TOC entry 256 (class 1259 OID 227914)
-- Name: fixture_player_stats; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.fixture_player_stats (
    fixture_id bigint NOT NULL,
    player_id bigint NOT NULL,
    team_id bigint NOT NULL,
    player_name character varying(255),
    "position" character varying(10),
    minutes_played integer,
    rating numeric(4,2),
    captain boolean DEFAULT false,
    substitute boolean DEFAULT false,
    offsides integer,
    shots_total integer,
    shots_on integer,
    goals_scored integer,
    goals_conceded integer,
    assists integer,
    saves integer,
    passes_total integer,
    passes_key integer,
    passes_accuracy numeric(5,2),
    tackles_total integer,
    tackles_blocks integer,
    interceptions integer,
    duels_total integer,
    duels_won integer,
    dribbles_att integer,
    dribbles_suc integer,
    dribbles_past integer,
    fouls_drawn integer,
    fouls_committed integer,
    yellow_cards integer,
    red_cards integer,
    yellow_red_cards integer,
    penalty_won integer,
    penalty_scored integer,
    penalty_missed integer,
    penalty_saved integer,
    penalty_committed integer
);


ALTER TABLE public.fixture_player_stats OWNER TO postgres;

--
-- TOC entry 255 (class 1259 OID 227885)
-- Name: fixture_team_stats; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.fixture_team_stats (
    fixture_id bigint NOT NULL,
    team_id bigint NOT NULL,
    shots_on_goal integer,
    shots_off_goal integer,
    shots_total integer,
    shots_blocked integer,
    shots_inside_box integer,
    shots_outside_box integer,
    fouls integer,
    corner_kicks integer,
    offsides integer,
    ball_possession numeric(5,2),
    yellow_cards integer,
    red_cards integer,
    goalkeeper_saves integer,
    total_passes integer,
    passes_accurate integer,
    passes_pct numeric(5,2),
    expected_goals numeric(5,2),
    goals_prevented numeric(5,2)
);


ALTER TABLE public.fixture_team_stats OWNER TO postgres;

--
-- TOC entry 219 (class 1259 OID 16411)
-- Name: manual_tracked_player; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.manual_tracked_player (
    id bigint NOT NULL,
    nombre character varying(255) NOT NULL,
    nota double precision NOT NULL,
    club character varying(255) NOT NULL,
    posicion character varying(255),
    most_like_destination character varying(255),
    likeable character varying(255),
    date date,
    birth date,
    age integer,
    fbrefid bigint,
    indexid bigint,
    id_user bigint,
    basicid bigint,
    photo bytea
);


ALTER TABLE public.manual_tracked_player OWNER TO postgres;

--
-- TOC entry 218 (class 1259 OID 16410)
-- Name: manual_tracked_data_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.manual_tracked_data_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.manual_tracked_data_id_seq OWNER TO postgres;

--
-- TOC entry 5093 (class 0 OID 0)
-- Dependencies: 218
-- Name: manual_tracked_data_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.manual_tracked_data_id_seq OWNED BY public.manual_tracked_player.id;


--
-- TOC entry 227 (class 1259 OID 49205)
-- Name: pais; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.pais (
    id integer NOT NULL,
    name character varying(255),
    code character varying(255)
);


ALTER TABLE public.pais OWNER TO postgres;

--
-- TOC entry 226 (class 1259 OID 49204)
-- Name: pais_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.pais_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.pais_id_seq OWNER TO postgres;

--
-- TOC entry 5094 (class 0 OID 0)
-- Dependencies: 226
-- Name: pais_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.pais_id_seq OWNED BY public.pais.id;


--
-- TOC entry 240 (class 1259 OID 82008)
-- Name: player_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.player_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.player_id_seq OWNER TO postgres;

--
-- TOC entry 235 (class 1259 OID 49267)
-- Name: player; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.player (
    id bigint DEFAULT nextval('public.player_id_seq'::regclass) NOT NULL,
    firstname character varying(255),
    lastname character varying(255),
    fullname character varying(255),
    nacionalidad integer,
    birth date,
    age integer,
    height integer,
    weight integer,
    injured boolean,
    team bigint NOT NULL,
    last_updated timestamp without time zone NOT NULL,
    index_id bigint,
    fbref_id bigint,
    isstudied boolean DEFAULT false,
    is_studied boolean,
    photo bytea,
    photo_content_type character varying(255),
    photo_updated_at timestamp without time zone
);


ALTER TABLE public.player OWNER TO postgres;

--
-- TOC entry 245 (class 1259 OID 106608)
-- Name: player_match_stats; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.player_match_stats (
    player_id bigint NOT NULL,
    player_name text,
    team_id integer,
    team_name text,
    league_id integer NOT NULL,
    league_name text,
    season text NOT NULL,
    match_bucket text NOT NULL,
    minutes integer,
    "position" text,
    rating numeric(4,2),
    shots_total integer,
    shots_on integer,
    goals integer,
    assists integer,
    passes_total integer,
    passes_key integer,
    passes_acc integer,
    tackles_total integer,
    interceptions integer,
    duels_total integer,
    duels_won integer,
    dribbles_att integer,
    dribbles_suc integer,
    fouls_drawn integer,
    fouls_comm integer,
    yc integer,
    rc integer
);


ALTER TABLE public.player_match_stats OWNER TO postgres;

--
-- TOC entry 220 (class 1259 OID 16421)
-- Name: player_qualities; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.player_qualities (
    player_id bigint NOT NULL,
    quality character varying(255) NOT NULL,
    id bigint NOT NULL
);


ALTER TABLE public.player_qualities OWNER TO postgres;

--
-- TOC entry 221 (class 1259 OID 16448)
-- Name: player_qualities_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.player_qualities_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.player_qualities_id_seq OWNER TO postgres;

--
-- TOC entry 5095 (class 0 OID 0)
-- Dependencies: 221
-- Name: player_qualities_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.player_qualities_id_seq OWNED BY public.player_qualities.id;


--
-- TOC entry 244 (class 1259 OID 106598)
-- Name: raw_ingest; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.raw_ingest (
    id bigint NOT NULL,
    source text NOT NULL,
    team_id integer,
    season text,
    filename text,
    payload_sha1 text NOT NULL,
    payload jsonb NOT NULL,
    ingested_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.raw_ingest OWNER TO postgres;

--
-- TOC entry 260 (class 1259 OID 256310)
-- Name: raw_ingest_2023; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.raw_ingest_2023 (
    id bigint NOT NULL,
    source text NOT NULL,
    team_id integer,
    season text,
    filename text,
    payload_sha1 text NOT NULL,
    payload jsonb NOT NULL,
    ingested_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.raw_ingest_2023 OWNER TO postgres;

--
-- TOC entry 259 (class 1259 OID 256309)
-- Name: raw_ingest_2023_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.raw_ingest_2023_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.raw_ingest_2023_id_seq OWNER TO postgres;

--
-- TOC entry 5096 (class 0 OID 0)
-- Dependencies: 259
-- Name: raw_ingest_2023_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.raw_ingest_2023_id_seq OWNED BY public.raw_ingest_2023.id;


--
-- TOC entry 262 (class 1259 OID 256323)
-- Name: raw_ingest_2024; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.raw_ingest_2024 (
    id bigint NOT NULL,
    source text NOT NULL,
    team_id integer,
    season text,
    filename text,
    payload_sha1 text NOT NULL,
    payload jsonb NOT NULL,
    ingested_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.raw_ingest_2024 OWNER TO postgres;

--
-- TOC entry 261 (class 1259 OID 256322)
-- Name: raw_ingest_2024_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.raw_ingest_2024_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.raw_ingest_2024_id_seq OWNER TO postgres;

--
-- TOC entry 5097 (class 0 OID 0)
-- Dependencies: 261
-- Name: raw_ingest_2024_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.raw_ingest_2024_id_seq OWNED BY public.raw_ingest_2024.id;


--
-- TOC entry 243 (class 1259 OID 106597)
-- Name: raw_ingest_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.raw_ingest_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.raw_ingest_id_seq OWNER TO postgres;

--
-- TOC entry 5098 (class 0 OID 0)
-- Dependencies: 243
-- Name: raw_ingest_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.raw_ingest_id_seq OWNED BY public.raw_ingest.id;


--
-- TOC entry 249 (class 1259 OID 172349)
-- Name: squad; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.squad (
    id bigint NOT NULL,
    team bigint NOT NULL,
    players bigint[] NOT NULL
);


ALTER TABLE public.squad OWNER TO postgres;

--
-- TOC entry 248 (class 1259 OID 172348)
-- Name: squad_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.squad_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.squad_id_seq OWNER TO postgres;

--
-- TOC entry 5099 (class 0 OID 0)
-- Dependencies: 248
-- Name: squad_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.squad_id_seq OWNED BY public.squad.id;


--
-- TOC entry 229 (class 1259 OID 49212)
-- Name: tipo_torneo; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.tipo_torneo (
    id integer NOT NULL,
    tipo character varying(255)
);


ALTER TABLE public.tipo_torneo OWNER TO postgres;

--
-- TOC entry 228 (class 1259 OID 49211)
-- Name: tipo_torneo_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.tipo_torneo_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.tipo_torneo_id_seq OWNER TO postgres;

--
-- TOC entry 5100 (class 0 OID 0)
-- Dependencies: 228
-- Name: tipo_torneo_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.tipo_torneo_id_seq OWNED BY public.tipo_torneo.id;


--
-- TOC entry 231 (class 1259 OID 49219)
-- Name: tipo_transfer; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.tipo_transfer (
    id integer NOT NULL,
    tipo character varying(100)
);


ALTER TABLE public.tipo_transfer OWNER TO postgres;

--
-- TOC entry 230 (class 1259 OID 49218)
-- Name: tipo_transfer_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.tipo_transfer_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.tipo_transfer_id_seq OWNER TO postgres;

--
-- TOC entry 5101 (class 0 OID 0)
-- Dependencies: 230
-- Name: tipo_transfer_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.tipo_transfer_id_seq OWNED BY public.tipo_transfer.id;


--
-- TOC entry 233 (class 1259 OID 49226)
-- Name: torneo; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.torneo (
    id bigint NOT NULL,
    name character varying(255),
    tipo integer,
    pais integer,
    studied boolean,
    fbrefdata boolean,
    fbrefid integer,
    lastupdate timestamp(6) without time zone
);


ALTER TABLE public.torneo OWNER TO postgres;

--
-- TOC entry 232 (class 1259 OID 49225)
-- Name: torneo_fbrefid_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.torneo_fbrefid_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.torneo_fbrefid_seq OWNER TO postgres;

--
-- TOC entry 5102 (class 0 OID 0)
-- Dependencies: 232
-- Name: torneo_fbrefid_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.torneo_fbrefid_seq OWNED BY public.torneo.fbrefid;


--
-- TOC entry 247 (class 1259 OID 172337)
-- Name: transfer; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.transfer (
    id bigint NOT NULL,
    player bigint NOT NULL,
    "in" bigint NOT NULL,
    "out" bigint NOT NULL,
    kind integer,
    season character varying(255) NOT NULL
);


ALTER TABLE public.transfer OWNER TO postgres;

--
-- TOC entry 246 (class 1259 OID 172336)
-- Name: transfer_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.transfer_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.transfer_id_seq OWNER TO postgres;

--
-- TOC entry 5103 (class 0 OID 0)
-- Dependencies: 246
-- Name: transfer_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.transfer_id_seq OWNED BY public.transfer.id;


--
-- TOC entry 4792 (class 2604 OID 41005)
-- Name: api_football_keys id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.api_football_keys ALTER COLUMN id SET DEFAULT nextval('public.api_football_keys_id_seq'::regclass);


--
-- TOC entry 4795 (class 2604 OID 41029)
-- Name: api_services id_service; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.api_services ALTER COLUMN id_service SET DEFAULT nextval('public.api_services_id_service_seq'::regclass);


--
-- TOC entry 4804 (class 2604 OID 82101)
-- Name: app_user id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.app_user ALTER COLUMN id SET DEFAULT nextval('public.app_user_id_seq'::regclass);


--
-- TOC entry 4803 (class 2604 OID 81995)
-- Name: club_in_league id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.club_in_league ALTER COLUMN id SET DEFAULT nextval('public.club_in_league_id_seq'::regclass);


--
-- TOC entry 4802 (class 2604 OID 73775)
-- Name: config_params id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.config_params ALTER COLUMN id SET DEFAULT nextval('public.config_params_id_seq'::regclass);


--
-- TOC entry 4810 (class 2604 OID 172361)
-- Name: dupped_players id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.dupped_players ALTER COLUMN id SET DEFAULT nextval('public.dupped_players_id_seq'::regclass);


--
-- TOC entry 4790 (class 2604 OID 16429)
-- Name: manual_tracked_player id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.manual_tracked_player ALTER COLUMN id SET DEFAULT nextval('public.manual_tracked_data_id_seq'::regclass);


--
-- TOC entry 4796 (class 2604 OID 49208)
-- Name: pais id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.pais ALTER COLUMN id SET DEFAULT nextval('public.pais_id_seq'::regclass);


--
-- TOC entry 4791 (class 2604 OID 16456)
-- Name: player_qualities id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.player_qualities ALTER COLUMN id SET DEFAULT nextval('public.player_qualities_id_seq'::regclass);


--
-- TOC entry 4806 (class 2604 OID 106601)
-- Name: raw_ingest id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.raw_ingest ALTER COLUMN id SET DEFAULT nextval('public.raw_ingest_id_seq'::regclass);


--
-- TOC entry 4822 (class 2604 OID 256313)
-- Name: raw_ingest_2023 id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.raw_ingest_2023 ALTER COLUMN id SET DEFAULT nextval('public.raw_ingest_2023_id_seq'::regclass);


--
-- TOC entry 4824 (class 2604 OID 256326)
-- Name: raw_ingest_2024 id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.raw_ingest_2024 ALTER COLUMN id SET DEFAULT nextval('public.raw_ingest_2024_id_seq'::regclass);


--
-- TOC entry 4809 (class 2604 OID 172352)
-- Name: squad id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.squad ALTER COLUMN id SET DEFAULT nextval('public.squad_id_seq'::regclass);


--
-- TOC entry 4797 (class 2604 OID 49215)
-- Name: tipo_torneo id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.tipo_torneo ALTER COLUMN id SET DEFAULT nextval('public.tipo_torneo_id_seq'::regclass);


--
-- TOC entry 4798 (class 2604 OID 49222)
-- Name: tipo_transfer id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.tipo_transfer ALTER COLUMN id SET DEFAULT nextval('public.tipo_transfer_id_seq'::regclass);


--
-- TOC entry 4799 (class 2604 OID 49229)
-- Name: torneo fbrefid; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.torneo ALTER COLUMN fbrefid SET DEFAULT nextval('public.torneo_fbrefid_seq'::regclass);


--
-- TOC entry 4808 (class 2604 OID 172340)
-- Name: transfer id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.transfer ALTER COLUMN id SET DEFAULT nextval('public.transfer_id_seq'::regclass);


--
-- TOC entry 4837 (class 2606 OID 41007)
-- Name: api_football_keys api_football_keys_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.api_football_keys
    ADD CONSTRAINT api_football_keys_pkey PRIMARY KEY (id);


--
-- TOC entry 4840 (class 2606 OID 41031)
-- Name: api_services api_services_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.api_services
    ADD CONSTRAINT api_services_pkey PRIMARY KEY (id_service);


--
-- TOC entry 4860 (class 2606 OID 82104)
-- Name: app_user app_user_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.app_user
    ADD CONSTRAINT app_user_pkey PRIMARY KEY (id);


--
-- TOC entry 4862 (class 2606 OID 82106)
-- Name: app_user app_user_username_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.app_user
    ADD CONSTRAINT app_user_username_key UNIQUE (username);


--
-- TOC entry 4858 (class 2606 OID 81997)
-- Name: club_in_league club_in_league_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.club_in_league
    ADD CONSTRAINT club_in_league_pkey PRIMARY KEY (id);


--
-- TOC entry 4850 (class 2606 OID 49246)
-- Name: club club_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.club
    ADD CONSTRAINT club_pkey PRIMARY KEY (id);


--
-- TOC entry 4856 (class 2606 OID 73777)
-- Name: config_params config_params_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.config_params
    ADD CONSTRAINT config_params_pkey PRIMARY KEY (id);


--
-- TOC entry 4883 (class 2606 OID 172363)
-- Name: dupped_players dupped_players_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.dupped_players
    ADD CONSTRAINT dupped_players_pkey PRIMARY KEY (id);


--
-- TOC entry 4893 (class 2606 OID 227870)
-- Name: fixture_event fixture_event_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.fixture_event
    ADD CONSTRAINT fixture_event_pkey PRIMARY KEY (id);


--
-- TOC entry 4910 (class 2606 OID 228172)
-- Name: fixture_lineup fixture_lineup_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.fixture_lineup
    ADD CONSTRAINT fixture_lineup_pkey PRIMARY KEY (id);


--
-- TOC entry 4885 (class 2606 OID 227845)
-- Name: fixture fixture_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.fixture
    ADD CONSTRAINT fixture_pkey PRIMARY KEY (id);


--
-- TOC entry 4903 (class 2606 OID 227920)
-- Name: fixture_player_stats fixture_player_stats_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.fixture_player_stats
    ADD CONSTRAINT fixture_player_stats_pkey PRIMARY KEY (fixture_id, player_id);


--
-- TOC entry 4899 (class 2606 OID 227889)
-- Name: fixture_team_stats fixture_team_stats_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.fixture_team_stats
    ADD CONSTRAINT fixture_team_stats_pkey PRIMARY KEY (fixture_id, team_id);


--
-- TOC entry 4827 (class 2606 OID 16431)
-- Name: manual_tracked_player manual_tracked_data_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.manual_tracked_player
    ADD CONSTRAINT manual_tracked_data_pkey PRIMARY KEY (id);


--
-- TOC entry 4842 (class 2606 OID 49210)
-- Name: pais pais_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.pais
    ADD CONSTRAINT pais_pkey PRIMARY KEY (id);


--
-- TOC entry 4873 (class 2606 OID 263951)
-- Name: player_match_stats player_match_stats_uq; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.player_match_stats
    ADD CONSTRAINT player_match_stats_uq UNIQUE (player_id, league_id, season, match_bucket);


--
-- TOC entry 4854 (class 2606 OID 49273)
-- Name: player player_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.player
    ADD CONSTRAINT player_pkey PRIMARY KEY (id);


--
-- TOC entry 4835 (class 2606 OID 16458)
-- Name: player_qualities player_qualities_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.player_qualities
    ADD CONSTRAINT player_qualities_pkey PRIMARY KEY (id);


--
-- TOC entry 4867 (class 2606 OID 106606)
-- Name: raw_ingest raw_ingest_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.raw_ingest
    ADD CONSTRAINT raw_ingest_pkey PRIMARY KEY (id);


--
-- TOC entry 4913 (class 2606 OID 256318)
-- Name: raw_ingest_2023 raw_ingest_pkey_23; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.raw_ingest_2023
    ADD CONSTRAINT raw_ingest_pkey_23 PRIMARY KEY (id);


--
-- TOC entry 4916 (class 2606 OID 256331)
-- Name: raw_ingest_2024 raw_ingest_pkey_24; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.raw_ingest_2024
    ADD CONSTRAINT raw_ingest_pkey_24 PRIMARY KEY (id);


--
-- TOC entry 4881 (class 2606 OID 172356)
-- Name: squad squad_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.squad
    ADD CONSTRAINT squad_pkey PRIMARY KEY (id);


--
-- TOC entry 4844 (class 2606 OID 49217)
-- Name: tipo_torneo tipo_torneo_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.tipo_torneo
    ADD CONSTRAINT tipo_torneo_pkey PRIMARY KEY (id);


--
-- TOC entry 4846 (class 2606 OID 49224)
-- Name: tipo_transfer tipo_transfer_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.tipo_transfer
    ADD CONSTRAINT tipo_transfer_pkey PRIMARY KEY (id);


--
-- TOC entry 4848 (class 2606 OID 49231)
-- Name: torneo torneo_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.torneo
    ADD CONSTRAINT torneo_pkey PRIMARY KEY (id);


--
-- TOC entry 4879 (class 2606 OID 172342)
-- Name: transfer transfer_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.transfer
    ADD CONSTRAINT transfer_pkey PRIMARY KEY (id);


--
-- TOC entry 4829 (class 2606 OID 16447)
-- Name: manual_tracked_player uk83r8jckpgnch8asi2lxqec55u; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.manual_tracked_player
    ADD CONSTRAINT uk83r8jckpgnch8asi2lxqec55u UNIQUE (nombre);


--
-- TOC entry 4831 (class 2606 OID 16445)
-- Name: manual_tracked_player ukir9985ps18y6788r096vgq39j; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.manual_tracked_player
    ADD CONSTRAINT ukir9985ps18y6788r096vgq39j UNIQUE (nombre);


--
-- TOC entry 4833 (class 2606 OID 82113)
-- Name: manual_tracked_player uq_manual_nombre_user; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.manual_tracked_player
    ADD CONSTRAINT uq_manual_nombre_user UNIQUE (nombre, id_user);


--
-- TOC entry 4838 (class 1259 OID 24643)
-- Name: idx_api_football_keys_id_service; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_api_football_keys_id_service ON public.api_football_keys USING btree (id_service);


--
-- TOC entry 4894 (class 1259 OID 227881)
-- Name: idx_fevent_fixture; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_fevent_fixture ON public.fixture_event USING btree (fixture_id);


--
-- TOC entry 4895 (class 1259 OID 227882)
-- Name: idx_fevent_player; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_fevent_player ON public.fixture_event USING btree (player_id);


--
-- TOC entry 4896 (class 1259 OID 227884)
-- Name: idx_fevent_team; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_fevent_team ON public.fixture_event USING btree (team_id);


--
-- TOC entry 4897 (class 1259 OID 227883)
-- Name: idx_fevent_type; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_fevent_type ON public.fixture_event USING btree (event_type);


--
-- TOC entry 4886 (class 1259 OID 227860)
-- Name: idx_fixture_away_team; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_fixture_away_team ON public.fixture USING btree (away_team_id);


--
-- TOC entry 4887 (class 1259 OID 227857)
-- Name: idx_fixture_date; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_fixture_date ON public.fixture USING btree (match_date);


--
-- TOC entry 4888 (class 1259 OID 227859)
-- Name: idx_fixture_home_team; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_fixture_home_team ON public.fixture USING btree (home_team_id);


--
-- TOC entry 4889 (class 1259 OID 227861)
-- Name: idx_fixture_league_date; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_fixture_league_date ON public.fixture USING btree (league_id, match_date);


--
-- TOC entry 4890 (class 1259 OID 227856)
-- Name: idx_fixture_league_season; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_fixture_league_season ON public.fixture USING btree (league_id, season);


--
-- TOC entry 4911 (class 1259 OID 228178)
-- Name: idx_fixture_lineup_fixture_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_fixture_lineup_fixture_id ON public.fixture_lineup USING btree (fixture_id);


--
-- TOC entry 4891 (class 1259 OID 227858)
-- Name: idx_fixture_status; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_fixture_status ON public.fixture USING btree (status_short);


--
-- TOC entry 4904 (class 1259 OID 227933)
-- Name: idx_fps_fixture; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_fps_fixture ON public.fixture_player_stats USING btree (fixture_id);


--
-- TOC entry 4905 (class 1259 OID 227931)
-- Name: idx_fps_player; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_fps_player ON public.fixture_player_stats USING btree (player_id);


--
-- TOC entry 4906 (class 1259 OID 227935)
-- Name: idx_fps_player_fixture; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_fps_player_fixture ON public.fixture_player_stats USING btree (player_id, fixture_id);


--
-- TOC entry 4907 (class 1259 OID 227934)
-- Name: idx_fps_position; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_fps_position ON public.fixture_player_stats USING btree ("position");


--
-- TOC entry 4908 (class 1259 OID 227932)
-- Name: idx_fps_team; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_fps_team ON public.fixture_player_stats USING btree (team_id);


--
-- TOC entry 4900 (class 1259 OID 227901)
-- Name: idx_fts_fixture; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_fts_fixture ON public.fixture_team_stats USING btree (fixture_id);


--
-- TOC entry 4901 (class 1259 OID 227900)
-- Name: idx_fts_team; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_fts_team ON public.fixture_team_stats USING btree (team_id);


--
-- TOC entry 4851 (class 1259 OID 82062)
-- Name: idx_player_index_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_player_index_id ON public.player USING btree (index_id);


--
-- TOC entry 4852 (class 1259 OID 82061)
-- Name: idx_player_search; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_player_search ON public.player USING btree (firstname, lastname, fullname);


--
-- TOC entry 4874 (class 1259 OID 106616)
-- Name: pms_idx_league_season; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX pms_idx_league_season ON public.player_match_stats USING btree (league_id, season);


--
-- TOC entry 4875 (class 1259 OID 106617)
-- Name: pms_idx_position; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX pms_idx_position ON public.player_match_stats USING btree ("position");


--
-- TOC entry 4876 (class 1259 OID 106657)
-- Name: pms_idx_team_pos; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX pms_idx_team_pos ON public.player_match_stats USING btree (team_id, "position");


--
-- TOC entry 4877 (class 1259 OID 106615)
-- Name: pms_idx_team_season; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX pms_idx_team_season ON public.player_match_stats USING btree (team_id, season);


--
-- TOC entry 4863 (class 1259 OID 106607)
-- Name: raw_ingest_dedupe; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX raw_ingest_dedupe ON public.raw_ingest USING btree (source, season, filename, payload_sha1);


--
-- TOC entry 4864 (class 1259 OID 256319)
-- Name: raw_ingest_dedupe_23; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX raw_ingest_dedupe_23 ON public.raw_ingest USING btree (source, season, filename, payload_sha1);


--
-- TOC entry 4865 (class 1259 OID 256332)
-- Name: raw_ingest_dedupe_24; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX raw_ingest_dedupe_24 ON public.raw_ingest USING btree (source, season, filename, payload_sha1);


--
-- TOC entry 4868 (class 1259 OID 106658)
-- Name: raw_payload_gin; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX raw_payload_gin ON public.raw_ingest USING gin (payload jsonb_path_ops);


--
-- TOC entry 4869 (class 1259 OID 256320)
-- Name: raw_payload_gin_23; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX raw_payload_gin_23 ON public.raw_ingest USING gin (payload jsonb_path_ops);


--
-- TOC entry 4870 (class 1259 OID 256333)
-- Name: raw_payload_gin_24; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX raw_payload_gin_24 ON public.raw_ingest USING gin (payload jsonb_path_ops);


--
-- TOC entry 4871 (class 1259 OID 109511)
-- Name: ux_raw_ingest_payload_sha1; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX ux_raw_ingest_payload_sha1 ON public.raw_ingest USING btree (payload_sha1);


--
-- TOC entry 4914 (class 1259 OID 256321)
-- Name: ux_raw_ingest_payload_sha1_23; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX ux_raw_ingest_payload_sha1_23 ON public.raw_ingest_2023 USING btree (payload_sha1);


--
-- TOC entry 4917 (class 1259 OID 256334)
-- Name: ux_raw_ingest_payload_sha1_24; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX ux_raw_ingest_payload_sha1_24 ON public.raw_ingest_2024 USING btree (payload_sha1);


--
-- TOC entry 4923 (class 2606 OID 49247)
-- Name: club club_pais_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.club
    ADD CONSTRAINT club_pais_fkey FOREIGN KEY (pais) REFERENCES public.pais(id);


--
-- TOC entry 4937 (class 2606 OID 228173)
-- Name: fixture_lineup fixture_lineup_fixture_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.fixture_lineup
    ADD CONSTRAINT fixture_lineup_fixture_id_fkey FOREIGN KEY (fixture_id) REFERENCES public.fixture(id);


--
-- TOC entry 4920 (class 2606 OID 41032)
-- Name: api_football_keys fk_api_football_keys_id_service; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.api_football_keys
    ADD CONSTRAINT fk_api_football_keys_id_service FOREIGN KEY (id_service) REFERENCES public.api_services(id_service);


--
-- TOC entry 4926 (class 2606 OID 81984)
-- Name: club_in_league fk_club; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.club_in_league
    ADD CONSTRAINT fk_club FOREIGN KEY (club) REFERENCES public.club(id);


--
-- TOC entry 4931 (class 2606 OID 227871)
-- Name: fixture_event fk_event_fixture; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.fixture_event
    ADD CONSTRAINT fk_event_fixture FOREIGN KEY (fixture_id) REFERENCES public.fixture(id) ON DELETE CASCADE;


--
-- TOC entry 4932 (class 2606 OID 227876)
-- Name: fixture_event fk_event_team; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.fixture_event
    ADD CONSTRAINT fk_event_team FOREIGN KEY (team_id) REFERENCES public.club(id);


--
-- TOC entry 4929 (class 2606 OID 227851)
-- Name: fixture fk_fixture_away_club; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.fixture
    ADD CONSTRAINT fk_fixture_away_club FOREIGN KEY (away_team_id) REFERENCES public.club(id);


--
-- TOC entry 4930 (class 2606 OID 227846)
-- Name: fixture fk_fixture_home_club; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.fixture
    ADD CONSTRAINT fk_fixture_home_club FOREIGN KEY (home_team_id) REFERENCES public.club(id);


--
-- TOC entry 4935 (class 2606 OID 227921)
-- Name: fixture_player_stats fk_fps_fixture; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.fixture_player_stats
    ADD CONSTRAINT fk_fps_fixture FOREIGN KEY (fixture_id) REFERENCES public.fixture(id) ON DELETE CASCADE;


--
-- TOC entry 4936 (class 2606 OID 227926)
-- Name: fixture_player_stats fk_fps_team; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.fixture_player_stats
    ADD CONSTRAINT fk_fps_team FOREIGN KEY (team_id) REFERENCES public.club(id);


--
-- TOC entry 4933 (class 2606 OID 227890)
-- Name: fixture_team_stats fk_fts_fixture; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.fixture_team_stats
    ADD CONSTRAINT fk_fts_fixture FOREIGN KEY (fixture_id) REFERENCES public.fixture(id) ON DELETE CASCADE;


--
-- TOC entry 4934 (class 2606 OID 227895)
-- Name: fixture_team_stats fk_fts_team; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.fixture_team_stats
    ADD CONSTRAINT fk_fts_team FOREIGN KEY (team_id) REFERENCES public.club(id);


--
-- TOC entry 4918 (class 2606 OID 82107)
-- Name: manual_tracked_player fk_manual_user; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.manual_tracked_player
    ADD CONSTRAINT fk_manual_user FOREIGN KEY (id_user) REFERENCES public.app_user(id);


--
-- TOC entry 4927 (class 2606 OID 81989)
-- Name: club_in_league fk_torneo; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.club_in_league
    ADD CONSTRAINT fk_torneo FOREIGN KEY (torneo) REFERENCES public.torneo(id);


--
-- TOC entry 4928 (class 2606 OID 172343)
-- Name: transfer fk_transfer_kind; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.transfer
    ADD CONSTRAINT fk_transfer_kind FOREIGN KEY (kind) REFERENCES public.tipo_transfer(id);


--
-- TOC entry 4924 (class 2606 OID 49274)
-- Name: player player_nacionalidad_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.player
    ADD CONSTRAINT player_nacionalidad_fkey FOREIGN KEY (nacionalidad) REFERENCES public.pais(id);


--
-- TOC entry 4919 (class 2606 OID 16432)
-- Name: player_qualities player_qualities_player_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.player_qualities
    ADD CONSTRAINT player_qualities_player_id_fkey FOREIGN KEY (player_id) REFERENCES public.manual_tracked_player(id);


--
-- TOC entry 4925 (class 2606 OID 49279)
-- Name: player player_team_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.player
    ADD CONSTRAINT player_team_fkey FOREIGN KEY (team) REFERENCES public.club(id);


--
-- TOC entry 4921 (class 2606 OID 49237)
-- Name: torneo torneo_pais_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.torneo
    ADD CONSTRAINT torneo_pais_fkey FOREIGN KEY (pais) REFERENCES public.pais(id);


--
-- TOC entry 4922 (class 2606 OID 49232)
-- Name: torneo torneo_tipo_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.torneo
    ADD CONSTRAINT torneo_tipo_fkey FOREIGN KEY (tipo) REFERENCES public.tipo_torneo(id);


-- Completed on 2026-05-07 14:14:03

--
-- PostgreSQL database dump complete
--

