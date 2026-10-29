-- MySQL schema for the *IT integration tests.
-- Reconstructed from the SQL in the repositories; replace with the Flyway
-- migrations once the real schema is versioned in the repo.

CREATE TABLE IF NOT EXISTS user (
    user_id       INT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    email         VARCHAR(150) NOT NULL UNIQUE,
    phone         VARCHAR(20),
    role          VARCHAR(20)  NOT NULL,
    gender        VARCHAR(10),
    dob           DATE,
    password_hash VARCHAR(255),
    password_salt VARCHAR(255),
    profile_photo LONGBLOB
);

CREATE TABLE IF NOT EXISTS business_owner (
    bo_id   INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES user (user_id)
);

CREATE TABLE IF NOT EXISTS flight_manager (
    fm_id   INT AUTO_INCREMENT PRIMARY KEY,
    bo_id   INT,
    user_id INT NOT NULL,
    FOREIGN KEY (bo_id) REFERENCES business_owner (bo_id),
    FOREIGN KEY (user_id) REFERENCES user (user_id)
);

CREATE TABLE IF NOT EXISTS airline (
    airline_id   INT AUTO_INCREMENT PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    code         VARCHAR(10)  NOT NULL UNIQUE,
    contact_info VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS airport (
    airport_id   INT AUTO_INCREMENT PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    code         VARCHAR(10)  NOT NULL UNIQUE,
    contact_info VARCHAR(255),
    location     VARCHAR(150)
);

CREATE TABLE IF NOT EXISTS flight (
    flight_id            INT AUTO_INCREMENT PRIMARY KEY,
    flight_number        VARCHAR(20) NOT NULL UNIQUE,
    airline_id           INT NOT NULL,
    departure_airport_id INT NOT NULL,
    arrival_airport_id   INT NOT NULL,
    departure_date_time  DATETIME NOT NULL,
    arrival_date_time    DATETIME NOT NULL,
    status               VARCHAR(20) NOT NULL,
    fm_id                INT,
    FOREIGN KEY (airline_id) REFERENCES airline (airline_id),
    FOREIGN KEY (departure_airport_id) REFERENCES airport (airport_id),
    FOREIGN KEY (arrival_airport_id) REFERENCES airport (airport_id),
    FOREIGN KEY (fm_id) REFERENCES flight_manager (fm_id)
);

CREATE TABLE IF NOT EXISTS seat_class (
    seat_class_id INT AUTO_INCREMENT PRIMARY KEY,
    class_name    VARCHAR(50) NOT NULL,
    price         DECIMAL(10, 2)
);

CREATE TABLE IF NOT EXISTS flight_seats (
    flight_seat_id  INT AUTO_INCREMENT PRIMARY KEY,
    flight_id       INT NOT NULL,
    seat_class_id   INT NOT NULL,
    available_seats INT NOT NULL,
    total_seats     INT NOT NULL,
    price           DECIMAL(10, 2),
    FOREIGN KEY (flight_id) REFERENCES flight (flight_id) ON DELETE CASCADE,
    FOREIGN KEY (seat_class_id) REFERENCES seat_class (seat_class_id)
);

CREATE TABLE IF NOT EXISTS action_log (
    log_id           INT AUTO_INCREMENT PRIMARY KEY,
    user_id          INT NOT NULL,
    action_performed VARCHAR(255) NOT NULL,
    controller_name  VARCHAR(100),
    timestamp        DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    email       VARCHAR(150) NOT NULL,
    token       VARCHAR(255) NOT NULL UNIQUE,
    expiry_date DATETIME NOT NULL
);
