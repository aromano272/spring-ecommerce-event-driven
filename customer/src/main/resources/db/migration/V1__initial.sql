CREATE TABLE IF NOT EXISTS customers
(
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    balance INT NOT NULL DEFAULT 0,
    reservedBalance INT NOT NULL DEFAULT 0,
    createdAt TIMESTAMP NOT NULL DEFAULT now(),
    updatedAt TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS reserved_balances
(
    userId INT NOT NULL,
    orderId INT NOT NULL,
    balance INT NOT NULL,
    createdAt TIMESTAMP NOT NULL DEFAULT now(),
    updatedAt TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE(userId, orderId)
);
