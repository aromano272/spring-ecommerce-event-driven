CREATE TABLE IF NOT EXISTS products
(
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name TEXT NOT NULL,
    price INT NOT NULL,
    inventory INT NOT NULL,
    reservedInventory INT NOT NULL DEFAULT 0,
    createdAt TIMESTAMP NOT NULL DEFAULT now(),
    updatedAt TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS reserved_products
(
    productId INT NOT NULL,
    userId INT NOT NULL,
    orderId INT NOT NULL,
    price INT NOT NULL,
    quantity INT NOT NULL,
    createdAt TIMESTAMP NOT NULL DEFAULT now(),
    updatedAt TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE(productId, userId, orderId)
);