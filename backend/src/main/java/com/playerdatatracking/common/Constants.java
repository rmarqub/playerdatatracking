package com.playerdatatracking.common;

public class Constants {
	
	
	public static final int CODE_OK = 0;
	
	//ERRORS
	public static final int CODE_ERR_MALFORMED_PARAMS = 1;
	public static final int CODE_ERR_PDDB = 2;
	public static final int CODE_ERR_INPUT_EXCEPTION = 3;
	public static final int CODE_ERR_XSL_READING_EXCEPTION = 4;
	public static final int CODE_ERR_PARSE_EXCEPTION = 5;
	public static final int CODE_ERR_NOT_FILLED_JSON_RESPONSE = 6;
	public static final int CODE_ERR_NOT_CREATED_JSON_RESPONSE = 7;
	public static final int CODE_ERR_GENERATED_KEY = 8;
	public static final int CODE_ERR_KEY_MNGMT = 9;
	public static final int CODE_ERR_NO_PLAYER_FOUND = 10;
	
	//API PLANS
	public static final String APISPORTS_FREE = "FREE";
	public static final String APISPORTS_PRO = "PRO";
	public static final String APISPORTS_ULTRA = "ULTRA";
	public static final String APISPORTS_MEGA = "MEGA";
	
	//API KEY NUMBER OF USAGES
	public static final int USAGES_APISPORTS_FREE = 100;
	public static final int USAGES_APISPORTS_PRO = 7500;
	public static final int USAGES_APISPORTS_ULTRA = 75000;
	public static final int USAGES_APISPORTS_MEGA = 150000;
	
	
	//COMMON
	public static final String CUP = "Cup";
	public static final int CUP_ID = 1;
	public static final String LEAGUE = "League";
	public static final int LEAGUE_ID = 2;
	public static final int COPA_INT_ID = 3;
	public static final int INTERNATIONAL_ID = 4;
	public static final String COPA = "copa";
	public static final String COPA_INTERNA = "copaINT";
	public static final String LIGA = "liga";
	public static final String INTERNACIONAL = "internacional";
	public static final String WORLD = "World";
	public static final String EUROPE = "Europe";
	public static final String ASIA = "Asia";
	public static final String OCEANIA = "Oceania";
	public static final String AMERICA = "America";
	public static final String AFRICA = "Africa";
	public static final String RATE_LIMIT_ERROR_MESSAGE = "Too many requests. Your rate limit is 300 requests per minute.";
	
	
	//TOURNAMENTS
	public static final String EURO = "Euro Championship";
	public static final String CONFEDERATIONS = "Confederations Cup";
	public static final String MUNDIAL = "World Cup";
	public static final String ASIAN_GAMES = "Asian Games";
	public static final String ASIAN_CUP = "Asian Cup";
	public static final String WWORLD_CUP = "World Cup - Women";
	public static final String OLYMPICS = "Olympics Men";
	public static final String CECAFA = "CECAFA Senior Challenge Cup";
	public static final String SAFF = "SAFF Championship";
	public static final String MUNDIAL_QF_PO = "World Cup - Qualification Intercontinental Play-offs";
	public static final String AFC = "AFC Challenge Cup";
	public static final String AFRICAN_NC = "African Nations Championship";
	public static final String AFF = "AFF Championship";
	public static final String GOLD_CUP = "CONCACAF Gold Cup";
	public static final String EAFF = "EAFF E-1 Football Championship";
	public static final String AFRICAN_CUP = "Africa Cup of Nations";
	public static final String COPA_AMERICA = "Copa America";
	public static final String WOLYMPICS = "Olympics Women";
	public static final String GULF_CUP = "Gulf Cup of Nations";
	public static final String OFC = "OFC Nations Cup";
	public static final String EURO_U21 = "UEFA U21 Championship";
	public static final String MUNDIAL_U20 = "World Cup - U20";
	public static final String EU_NATIONS_LEAGUE = "UEFA Nations League";
	public static final String CONCACAF_QF = "CONCACAF Nations League - Qualification";
	public static final String CF_MUNDIAL_QF = "World Cup - Qualification CONCACAF";
	public static final String EU_MUNDIAL_QF = "World Cup - Qualification Europe";
	public static final String OC_MUNDIAL_QF = "World Cup - Qualification Oceania";
	public static final String SAA_MUNDIAL_QF = "World Cup - Qualification South America";
	public static final String AF_MUNDIAL_QF = "World Cup - Qualification Africa";
	public static final String AS_MUNDIAL_QF = "World Cup - Qualification Asia";
	public static final String EURO_U19 = "UEFA U19 Championship";
	public static final String CONCACAF_U20 = "CONCACAF U20";
	public static final String ASIAN_CUP_QF = "Asian Cup - Qualification";
	public static final String PACIFIC_GAMES = "Pacific Games";
	public static final String SUDAMERICANO_U20 = "Sudamericano U20";
	public static final String MUNDIAL_U17 = "World Cup - U17";
	public static final String COSAFA_U20 = "COSAFA U20 Championship";
	public static final String ASIAN_CUP_U23 = "AFC U23 Asian Cup";
	public static final String AF_NATIONS_LEAGUE = "CONCACAF Nations League";
	public static final String AF_NATIONS_CUP_U20 = "Africa Cup of Nations U20";
	public static final String AF_OLYMPICS_QF = "Olympics Men - Qualification Concacaf";
	public static final String GOLD_CUP_QF = "CONCACAF Gold Cup - Qualification";
	public static final String COSAFA = "COSAFA Cup";
	public static final String ARAB_CUP = "Arab Cup";
	public static final String U20_ELITE = "U20 Elite League";
	public static final String AF_CUP_NATIONS_QF = "Africa Cup of Nations - Qualification";
	public static final String SAA_YOUTH_GAMES = "South American Youth Games";
	public static final String AFC_U23_QF = "AFC U23 Asian Cup - Qualification";
	public static final String AF_U23_CUP_NATIONS_QF = "Africa U23 Cup of Nations - Qualification";
	public static final String EU_U17_QF = "UEFA U17 Championship - Qualification";
	public static final String EU_U19_QF = "UEFA U19 Championship - Qualification";
	public static final String AFF_U23 = "AFF U23 Championship";
	public static final String SEAS_GAMES = "Southeast Asian Games";
	public static final String FINALISSIMA = "CONMEBOL - UEFA Finalissima";
	public static final String MEDITERRANEAN_GAMES = "Mediterranean Games";
	public static final String EU_U17 = "UEFA U17 Championship";
	public static final String AFF_U19 = "AFF U19 Championship";
	public static final String ARAB_CUP_U20 = "Arab Championship - U20";
	public static final String CAC_GAMES = "CAC Games";
	public static final String AFC_U17 = "AFC U17 Asian Cup";
	public static final String CAF_U23_CUP = "CAF U23 Cup of Nations";
	public static final String CONCACAF_CAA = "Concacaf Central American Cup";
	public static final String AF_GAMES = "All Africa Games";
	public static final String EU_U21_QF = "UEFA U21 Championship - Qualification";
	public static final String EURO_QF = "Euro Championship - Qualification";
	public static final String CONCACAF_U17 = "CONCACAF U17";
	public static final String AFC_U20 = "AFC U20 Asian Cup";
	public static final String CONMEBOL_U17 = "CONMEBOL - U17";
	public static final String CAF_U17 = "CAF Cup of Nations - U17";
	public static final String CAFA = "CAFA Nations Cup";
	public static final String CONMEBOL_OLYMPICS_QF = "CONMEBOL - Pre-Olympic Tournament";
	public static final String CONCACAF_U20_QF = "CONCACAF U20 - Qualification";
	public static final String WAFF_U23 = "WAFF Championship U23";
	public static final String OLYMPICS_QF = "Olympics - Intercontinental Play-offs";
	public static final String OFC_U19 = "OFC U19 Championship";
	
	//CONFIG_PARAMS
	public static final String ACTUAL_APF_SEASON = "ACTUAL_SEASON";
	
}
