DROP TABLE IF EXISTS item;

CREATE TABLE item (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    title       VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    price       DECIMAL(10, 2) NOT NULL,
    category    VARCHAR(20)  DEFAULT 'other',
    seller      VARCHAR(50)  NOT NULL,
    contact     VARCHAR(100),
    status      INT          DEFAULT 0,
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);
