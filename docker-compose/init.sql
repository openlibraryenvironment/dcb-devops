select 1;

CREATE USER kc_reshare_dcb WITH PASSWORD 'kc_reshare_dcb';
CREATE USER reshare_dcb WITH PASSWORD 'reshare_dcb';

DROP DATABASE if exists kc_reshare_dcb;
CREATE DATABASE kc_reshare_dcb;
GRANT ALL PRIVILEGES ON DATABASE kc_reshare_dcb to kc_reshare_dcb;

DROP DATABASE if exists reshare_dcb;
CREATE DATABASE reshare_dcb;
GRANT ALL PRIVILEGES ON DATABASE reshare_dcb to reshare_dcb;

CREATE EXTENSION IF NOT EXISTS pg_trgm;
