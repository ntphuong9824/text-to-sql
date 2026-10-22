-- Star schema `dw` built from raw `chinook`. Re-runnable by a superuser.
DROP SCHEMA IF EXISTS dw CASCADE;
CREATE SCHEMA dw;

-- Country -> region mapping (every other Chinook country is Europe).
CREATE TABLE dw.country_region AS
SELECT c.country::text AS country,
       CASE
           WHEN c.country IN ('USA', 'Canada') THEN 'North America'
           WHEN c.country IN ('Brazil', 'Argentina', 'Chile') THEN 'South America'
           WHEN c.country IN ('Australia', 'India') THEN 'Asia-Pacific'
           ELSE 'Europe'
       END::text AS region
FROM (SELECT DISTINCT country FROM chinook.customer WHERE country IS NOT NULL) c;
ALTER TABLE dw.country_region ADD PRIMARY KEY (country);

CREATE TABLE dw.dim_date AS
SELECT to_char(d, 'YYYYMMDD')::int AS date_key,
       d::date AS full_date,
       extract(day FROM d)::int AS day,
       extract(month FROM d)::int AS month,
       trim(to_char(d, 'Month'))::text AS month_name,
       extract(quarter FROM d)::int AS quarter,
       extract(year FROM d)::int AS year
FROM generate_series(
        (SELECT min(invoice_date)::date FROM chinook.invoice),
        (SELECT max(invoice_date)::date FROM chinook.invoice),
        interval '1 day') AS g(d);
ALTER TABLE dw.dim_date ADD PRIMARY KEY (date_key);

CREATE TABLE dw.dim_customer AS
SELECT c.customer_id AS customer_key,
       (c.first_name || ' ' || c.last_name)::text AS full_name,
       c.company::text AS company,
       c.city::text AS city,
       c.state::text AS state,
       c.country::text AS country,
       cr.region
FROM chinook.customer c
LEFT JOIN dw.country_region cr ON cr.country = c.country;
ALTER TABLE dw.dim_customer ADD PRIMARY KEY (customer_key);

CREATE TABLE dw.dim_employee AS
SELECT e.employee_id AS employee_key,
       (e.first_name || ' ' || e.last_name)::text AS full_name,
       e.title::text AS title,
       e.city::text AS city,
       e.country::text AS country
FROM chinook.employee e;
ALTER TABLE dw.dim_employee ADD PRIMARY KEY (employee_key);

CREATE TABLE dw.dim_track AS
SELECT t.track_id AS track_key,
       t.name::text AS track_name,
       al.title::text AS album_title,
       ar.name::text AS artist_name,
       g.name::text AS genre_name,
       mt.name::text AS media_type_name,
       t.composer::text AS composer,
       t.milliseconds::int AS milliseconds,
       t.unit_price::numeric(10,2) AS unit_price
FROM chinook.track t
LEFT JOIN chinook.album al ON al.album_id = t.album_id
LEFT JOIN chinook.artist ar ON ar.artist_id = al.artist_id
LEFT JOIN chinook.genre g ON g.genre_id = t.genre_id
LEFT JOIN chinook.media_type mt ON mt.media_type_id = t.media_type_id;
ALTER TABLE dw.dim_track ADD PRIMARY KEY (track_key);

CREATE TABLE dw.fact_sales AS
SELECT il.invoice_line_id AS sales_key,
       il.invoice_id AS invoice_id,
       to_char(i.invoice_date, 'YYYYMMDD')::int AS date_key,
       i.customer_id AS customer_key,
       c.support_rep_id AS employee_key,
       il.track_id AS track_key,
       il.quantity::int AS quantity,
       il.unit_price::numeric(10,2) AS unit_price,
       (il.unit_price * il.quantity)::numeric(10,2) AS revenue
FROM chinook.invoice_line il
JOIN chinook.invoice i ON i.invoice_id = il.invoice_id
JOIN chinook.customer c ON c.customer_id = i.customer_id;
ALTER TABLE dw.fact_sales ADD PRIMARY KEY (sales_key);
ALTER TABLE dw.fact_sales ADD FOREIGN KEY (date_key) REFERENCES dw.dim_date (date_key);
ALTER TABLE dw.fact_sales ADD FOREIGN KEY (customer_key) REFERENCES dw.dim_customer (customer_key);
ALTER TABLE dw.fact_sales ADD FOREIGN KEY (employee_key) REFERENCES dw.dim_employee (employee_key);
ALTER TABLE dw.fact_sales ADD FOREIGN KEY (track_key) REFERENCES dw.dim_track (track_key);

-- Descriptions (cross-checked against the semantic layer).
COMMENT ON TABLE dw.country_region IS 'Mapping from customer country to sales region.';
COMMENT ON COLUMN dw.country_region.country IS 'Country name as in Chinook.';
COMMENT ON COLUMN dw.country_region.region IS 'Sales region: North America, South America, Asia-Pacific or Europe.';

COMMENT ON TABLE dw.dim_date IS 'Calendar dimension: one row per day from first to last invoice date.';
COMMENT ON COLUMN dw.dim_date.date_key IS 'Date as integer yyyymmdd (primary key).';
COMMENT ON COLUMN dw.dim_date.full_date IS 'Calendar date.';
COMMENT ON COLUMN dw.dim_date.day IS 'Day of month (1-31).';
COMMENT ON COLUMN dw.dim_date.month IS 'Month number (1-12).';
COMMENT ON COLUMN dw.dim_date.month_name IS 'English month name, e.g. September.';
COMMENT ON COLUMN dw.dim_date.quarter IS 'Quarter of the year (1-4).';
COMMENT ON COLUMN dw.dim_date.year IS 'Calendar year.';

COMMENT ON TABLE dw.dim_customer IS 'Customer dimension, one row per customer.';
COMMENT ON COLUMN dw.dim_customer.customer_key IS 'Customer id (primary key).';
COMMENT ON COLUMN dw.dim_customer.full_name IS 'Customer first and last name.';
COMMENT ON COLUMN dw.dim_customer.company IS 'Company name, may be null.';
COMMENT ON COLUMN dw.dim_customer.city IS 'Customer city.';
COMMENT ON COLUMN dw.dim_customer.state IS 'Customer state or province, may be null.';
COMMENT ON COLUMN dw.dim_customer.country IS 'Customer country.';
COMMENT ON COLUMN dw.dim_customer.region IS 'Sales region derived from country: North America, South America, Asia-Pacific, Europe.';

COMMENT ON TABLE dw.dim_employee IS 'Employee dimension (sales support representatives and managers).';
COMMENT ON COLUMN dw.dim_employee.employee_key IS 'Employee id (primary key).';
COMMENT ON COLUMN dw.dim_employee.full_name IS 'Employee first and last name.';
COMMENT ON COLUMN dw.dim_employee.title IS 'Job title.';
COMMENT ON COLUMN dw.dim_employee.city IS 'Employee city.';
COMMENT ON COLUMN dw.dim_employee.country IS 'Employee country.';

COMMENT ON TABLE dw.dim_track IS 'Track dimension with album, artist, genre and media type flattened in.';
COMMENT ON COLUMN dw.dim_track.track_key IS 'Track id (primary key).';
COMMENT ON COLUMN dw.dim_track.track_name IS 'Track title.';
COMMENT ON COLUMN dw.dim_track.album_title IS 'Album title.';
COMMENT ON COLUMN dw.dim_track.artist_name IS 'Artist name.';
COMMENT ON COLUMN dw.dim_track.genre_name IS 'Genre name, may be null.';
COMMENT ON COLUMN dw.dim_track.media_type_name IS 'Media type name, e.g. MPEG audio file.';
COMMENT ON COLUMN dw.dim_track.composer IS 'Composer, may be null.';
COMMENT ON COLUMN dw.dim_track.milliseconds IS 'Track length in milliseconds.';
COMMENT ON COLUMN dw.dim_track.unit_price IS 'List price of the track.';

COMMENT ON TABLE dw.fact_sales IS 'Sales fact, one row per invoice line.';
COMMENT ON COLUMN dw.fact_sales.sales_key IS 'Invoice line id (primary key).';
COMMENT ON COLUMN dw.fact_sales.invoice_id IS 'Invoice id (degenerate dimension).';
COMMENT ON COLUMN dw.fact_sales.date_key IS 'Invoice date, FK to dim_date.';
COMMENT ON COLUMN dw.fact_sales.customer_key IS 'Buying customer, FK to dim_customer.';
COMMENT ON COLUMN dw.fact_sales.employee_key IS 'Support rep of the customer, FK to dim_employee, may be null.';
COMMENT ON COLUMN dw.fact_sales.track_key IS 'Track sold, FK to dim_track.';
COMMENT ON COLUMN dw.fact_sales.quantity IS 'Units sold on this line.';
COMMENT ON COLUMN dw.fact_sales.unit_price IS 'Price per unit at sale time.';
COMMENT ON COLUMN dw.fact_sales.revenue IS 'unit_price * quantity.';

-- Read-only access for the analyst role.
GRANT USAGE ON SCHEMA dw TO analyst_ro;
GRANT SELECT ON ALL TABLES IN SCHEMA dw TO analyst_ro;
