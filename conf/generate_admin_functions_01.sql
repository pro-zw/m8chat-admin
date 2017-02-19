-- psql --host 'localhost' --username 'weizheng' -d 'm8chat_stage' -f conf/generate_admin_functions_01.sql

-- Dashboard

DROP FUNCTION IF EXISTS public.admin_get_dashboard_statistics();

CREATE OR REPLACE FUNCTION public.admin_get_dashboard_statistics(OUT _user_count INTEGER, OUT _advertiser_count INTEGER, OUT _active_advert_count INTEGER, OUT _total_revenue NUMERIC)
  AS $$
BEGIN
  SELECT COUNT(*) INTO _user_count FROM social.m8_users;
  SELECT COUNT(*) INTO _advertiser_count FROM advert.advertisers;
  SELECT COUNT(*) INTO _active_advert_count FROM advert.advertisers WHERE status = 'active'::advertiser_status;
  SELECT COALESCE(SUM(amount), 0.00) INTO _total_revenue FROM advert.bills WHERE status = 'paid'::bill_status;
END;
$$
LANGUAGE 'plpgsql' STABLE;

-- Advertisers

DROP FUNCTION IF EXISTS advert.admin_get_advertisers();

CREATE OR REPLACE FUNCTION advert.admin_get_advertisers()
  RETURNS TABLE (_advertiser_id INTEGER, _name TEXT, _company_name TEXT, _email TEXT, _active_util TIMESTAMPTZ, _plan_name TEXT, _price NUMERIC, _status TEXT) AS $$
BEGIN
  RETURN QUERY
  SELECT advertiser_id, "name", company_name, email, active_util, plan_name::TEXT, price, status::TEXT
  FROM advert.advertisers;
END;
$$
LANGUAGE 'plpgsql' STABLE;

DROP FUNCTION IF EXISTS advert.admin_add_gold_advertisers(TEXT, TEXT, TEXT, TEXT, NUMERIC, INTEGER, INTEGER, TEXT);

CREATE OR REPLACE FUNCTION advert.admin_add_gold_advertisers(IN _name TEXT, IN _company_name TEXT, IN _email TEXT, IN _password TEXT, IN _price NUMERIC, IN _priority INTEGER, IN _photo_limit INTEGER, IN _email_confirm_token TEXT, OUT _advertiser_id INTEGER)
  AS $$
BEGIN
  INSERT INTO advert.advertisers ("name", company_name, email, "password", plan_name, price, priority, photo_limit, email_confirm_token)
  VALUES (_name, _company_name, lower(_email), _password, 'gold'::plan_name, _price, _priority, _photo_limit, _email_confirm_token)
  RETURNING advertiser_id INTO _advertiser_id;

  IF _advertiser_id <= 0 THEN
    RAISE EXCEPTION 'Unable to create the gold advertiser. Please try again' USING HINT = 'Duplicated email exists?', ERRCODE = 'P9999';
  END IF;
END;
$$
LANGUAGE 'plpgsql' VOLATILE;

DROP FUNCTION IF EXISTS advert.admin_get_advertiser_basic_info(INTEGER);

CREATE OR REPLACE FUNCTION advert.admin_get_advertiser_basic_info(IN _advertiser_id INTEGER)
  RETURNS TABLE(_name TEXT, _company_name TEXT, _email TEXT, _status TEXT, _created_at TIMESTAMPTZ, _active_util TIMESTAMPTZ, _suspended_reason TEXT, _admin_note TEXT) AS $$
BEGIN
  RETURN QUERY
    SELECT name, company_name, email, status::TEXT, created_at, active_util, suspended_reason, admin_note
    FROM advert.advertisers
    WHERE advertiser_id = _advertiser_id;
END;
$$
LANGUAGE 'plpgsql' STABLE;

DROP FUNCTION IF EXISTS advert.admin_save_advertiser_basic_info(INTEGER, TEXT, TEXT, TEXT, TEXT, TEXT);

CREATE OR REPLACE FUNCTION advert.admin_save_advertiser_basic_info(IN _advertiser_id INTEGER, IN _name TEXT, IN _company_name TEXT, IN _email TEXT, IN _suspended_reason TEXT, IN _admin_note TEXT)
  RETURNS VOID AS $$
DECLARE
  _update_count INT;
BEGIN
  UPDATE advert.advertisers
  SET "name" = _name, company_name = _company_name, email = lower(_email), suspended_reason = _suspended_reason, admin_note = _admin_note
  WHERE advertiser_id = _advertiser_id;

  GET DIAGNOSTICS _update_count = ROW_COUNT;

  IF _update_count < 1 THEN
    RAISE EXCEPTION 'Unable to save the advertiser''s basic information. Please try again' USING HINT = 'Invalid advertiser id provided?', ERRCODE = 'P9999';
  END IF;
END;
$$
LANGUAGE 'plpgsql' VOLATILE;

DROP FUNCTION IF EXISTS advert.admin_suspend_advertiser(INTEGER, TEXT);

CREATE OR REPLACE FUNCTION advert.admin_suspend_advertiser(IN _advertiser_id INTEGER, IN _suspended_reason TEXT)
  RETURNS VOID AS $$
DECLARE
  _update_count INT;
BEGIN
  PERFORM advert.charge_balance(_advertiser_id);

  UPDATE advert.bills
  SET status = 'canceled'::bill_status
  WHERE advertiser_id = _advertiser_id AND status = 'issued'::bill_status;

  UPDATE advert.advertisers
  SET status = 'suspended'::advertiser_status, suspended_reason = _suspended_reason
  WHERE advertiser_id = _advertiser_id;

  GET DIAGNOSTICS _update_count = ROW_COUNT;

  IF _update_count < 1 THEN
    RAISE EXCEPTION 'Unable to suspend the advertiser. Please try again' USING HINT = 'Invalid advertiser id provided?', ERRCODE = 'P9999';
  END IF;
END;
$$
LANGUAGE 'plpgsql' VOLATILE;

DROP FUNCTION IF EXISTS advert.admin_resume_advertiser(INTEGER);

CREATE OR REPLACE FUNCTION advert.admin_resume_advertiser(IN _advertiser_id INTEGER)
  RETURNS VOID AS $$
BEGIN
  UPDATE advert.advertisers
  SET status = CASE WHEN balance > 0 THEN 'active'::advertiser_status ELSE 'confirmed'::advertiser_status END, suspended_reason = DEFAULT, active_util = CASE WHEN active_util NOTNULL THEN current_timestamp + interval '1 month' * (balance / price) ELSE NULL END
  WHERE advertiser_id = _advertiser_id AND status = 'suspended'::advertiser_status;
END;
$$
LANGUAGE 'plpgsql' VOLATILE;

DROP FUNCTION IF EXISTS advert.admin_get_advertiser_business(INTEGER);

CREATE OR REPLACE FUNCTION advert.admin_get_advertiser_business(IN _advertiser_id INTEGER)
  RETURNS TABLE (_advert_id INTEGER, _name TEXT, _business_name TEXT, _contact_number TEXT, _website TEXT, _address TEXT, _latitude DOUBLE PRECISION, _longitude DOUBLE PRECISION, _description TEXT, _displayed_times INTEGER) AS $$
BEGIN
  RETURN QUERY
    SELECT ad.advert_id, av."name", ad.business_name, ad.contact_number, ad.website, ad.address, ST_Y(ad."position"::geometry)::DOUBLE PRECISION, ST_X(ad."position"::geometry)::DOUBLE PRECISION, ad.description, ad.displayed_times
    FROM advert.adverts ad INNER JOIN advert.advertisers av ON (ad.advertiser_id = av.advertiser_id)
    WHERE ad.advertiser_id = _advertiser_id;
END;
$$
LANGUAGE 'plpgsql' STABLE;

DROP FUNCTION IF EXISTS advert.admin_save_advertiser_business(INTEGER, INTEGER, TEXT, TEXT, TEXT, TEXT, DOUBLE PRECISION, DOUBLE PRECISION, TEXT);

CREATE OR REPLACE FUNCTION advert.admin_save_advertiser_business(IN _advertiser_id INTEGER, IN _advert_id INTEGER, IN _business_name TEXT, IN _contact_number TEXT, IN _website TEXT, IN _address TEXT, IN _latitude DOUBLE PRECISION, IN _longitude DOUBLE PRECISION, IN _description TEXT)
  RETURNS VOID AS $$
DECLARE
  _update_count INT;
BEGIN
  IF _advert_id <= 0 THEN
    INSERT INTO advert.adverts (advertiser_id, business_name, contact_number, website, address, "position", description)
    VALUES (_advertiser_id, _business_name, _contact_number, _website, _address, ST_GeogFromText('SRID=4326;POINT(' || _longitude || ' ' || _latitude || ')'), _description);
  ELSE
    UPDATE advert.adverts
    SET business_name = _business_name, contact_number = _contact_number, website = _website, address = _address, "position" = ST_GeogFromText('SRID=4326;POINT(' || _longitude || ' ' || _latitude || ')'), description = _description
    WHERE advertiser_id = _advertiser_id AND advert_id = _advert_id;
  END IF;

  GET DIAGNOSTICS _update_count = ROW_COUNT;

  IF _update_count < 1 THEN
    RAISE EXCEPTION 'Unable to save the advertiser''s advert information. Please try again' USING HINT = 'Invalid advertiser and/or advert id provided?', ERRCODE = 'P9999';
  END IF;
END;
$$
LANGUAGE 'plpgsql' VOLATILE;

DROP FUNCTION IF EXISTS advert.admin_get_advertiser_advert_photos(INTEGER);

CREATE OR REPLACE FUNCTION advert.admin_get_advertiser_advert_photos(IN _advertiser_id INTEGER)
  RETURNS TABLE (_name TEXT, _photos TEXT ARRAY) AS $$
BEGIN
  RETURN QUERY
    SELECT ar.name::TEXT, av.photos[1:ar.photo_limit]
    FROM advert.adverts av INNER JOIN advert.advertisers ar ON (av.advertiser_id = ar.advertiser_id)
    WHERE av.advertiser_id = _advertiser_id;
END;
$$
LANGUAGE 'plpgsql' VOLATILE;

DROP FUNCTION IF EXISTS advert.admin_reorganise_advertiser_advert_photos(INTEGER);

CREATE OR REPLACE FUNCTION advert.admin_reorganise_advertiser_advert_photos(IN _advertiser_id INTEGER)
  RETURNS VOID AS $$
BEGIN
  UPDATE advert.adverts
  SET photos = array_cat(array_remove(photos, ''), array_fill(''::TEXT, ARRAY[20]))
  WHERE advertiser_id = _advertiser_id;
END;
$$
LANGUAGE 'plpgsql' VOLATILE;

DROP FUNCTION IF EXISTS advert.admin_get_advertiser_bills(INTEGER);

CREATE OR REPLACE FUNCTION advert.admin_get_advertiser_bills(IN _advertiser_id INTEGER)
  RETURNS TABLE (_bill_id INTEGER, _issued_at TIMESTAMPTZ, _paid_at TIMESTAMPTZ, _expiring_at TIMESTAMPTZ, _canceled_at TIMESTAMPTZ, _amount NUMERIC, _payment_id TEXT, _status TEXT) AS $$
BEGIN
  RETURN QUERY
    SELECT bill_id, issued_at, paid_at, expiring_at, canceled_at, amount, payment_id, status::TEXT
    FROM advert.bills
    WHERE advertiser_id = _advertiser_id;
END;
$$
LANGUAGE 'plpgsql' STABLE;

-- Mobile Users

DROP FUNCTION IF EXISTS social.admin_get_users();

CREATE OR REPLACE FUNCTION social.admin_get_users()
  RETURNS TABLE (_user_id INTEGER, _username TEXT, _first_name TEXT, _email TEXT, _gender TEXT, _create_at TIMESTAMPTZ, _blocked_times INTEGER) AS $$
BEGIN
  RETURN QUERY
    SELECT user_id, username, first_name, email, gender::TEXT, created_at, blocked_times
    FROM social.m8_users;
END;
$$
LANGUAGE 'plpgsql' STABLE;

DROP FUNCTION IF EXISTS social.admin_get_user_basic_info(IN _user_id INTEGER);

CREATE OR REPLACE FUNCTION social.admin_get_user_basic_info(IN _user_id INTEGER)
  RETURNS TABLE(_email TEXT, _username TEXT, _first_name TEXT, _gender TEXT, _prefer_gender TEXT, _description TEXT, _created_at TIMESTAMPTZ, _latitude DOUBLE PRECISION, _longitude DOUBLE PRECISION, _interests TEXT, _blocked_times INT, _admin_note TEXT) AS $$
BEGIN
  RETURN QUERY
    SELECT email, username, first_name, gender::TEXT, prefer_gender::TEXT, description, created_at, ST_Y("position"::geometry)::DOUBLE PRECISION, ST_X("position"::geometry)::DOUBLE PRECISION, interests::TEXT, blocked_times, admin_note
    FROM social.m8_users
    WHERE user_id =  _user_id;
END;
$$
LANGUAGE 'plpgsql' STABLE;

DROP FUNCTION IF EXISTS social.admin_save_user_basic_info(INTEGER, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, DOUBLE PRECISION, DOUBLE PRECISION, INT, TEXT);

CREATE OR REPLACE FUNCTION social.admin_save_user_basic_info(IN _user_id INTEGER, IN _email TEXT, IN _username TEXT, IN _first_name TEXT, IN _gender TEXT, IN _prefer_gender TEXT, IN _description TEXT, IN _latitude DOUBLE PRECISION, IN _longitude DOUBLE PRECISION, IN _blocked_times INT, IN _admin_note TEXT)
  RETURNS VOID AS $$
DECLARE
  _update_count INT;
BEGIN
  UPDATE social.m8_users
  SET email = lower(_email), username = _username, first_name = _first_name, gender = _gender::gender, prefer_gender = _prefer_gender::gender, description = _description, "position" = ST_GeogFromText('SRID=4326;POINT(' || _longitude || ' ' || _latitude || ')'), blocked_times = _blocked_times, admin_note = _admin_note
  WHERE user_id = _user_id;

  GET DIAGNOSTICS _update_count = ROW_COUNT;

  IF _update_count < 1 THEN
    RAISE EXCEPTION 'Unable to save the mobile user''s basic information. Please try again' USING HINT = 'Invalid user id provided?', ERRCODE = 'P9999';
  END IF;
END;
$$
LANGUAGE 'plpgsql' VOLATILE;

DROP FUNCTION IF EXISTS social.admin_reorganise_user_pictures(INTEGER);

CREATE OR REPLACE FUNCTION social.admin_reorganise_user_pictures(IN _user_id INTEGER)
  RETURNS VOID AS $$
BEGIN
  UPDATE social.m8_users
  SET pictures = array_cat(array_remove(pictures, ''), array_fill(''::TEXT, ARRAY[6]))
  WHERE user_id = _user_id;
END;
$$
LANGUAGE 'plpgsql' VOLATILE;