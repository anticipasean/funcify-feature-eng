create table customer
(
    customer_id    int GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    first_name     varchar(500) null,
    middle_initial varchar(1) null,
    last_name      varchar(500) null
);

create table drink
(
    drink_id   int GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    drink_name varchar(100)   not null,
    drink_cost numeric(19, 2) not null,
    constraint uq_drink_name unique (drink_name)
);

create table drinkorder
(
    drink_order_id    int GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id       int                      not null,
    drink_order_total numeric(19, 2)                    default 0.00,
    created           timestamp with time zone not null default current_timestamp(),
    constraint fk_drinkorder_customer_id foreign key (customer_id) references customer
        (customer_id)
);

create table drinkorderitems
(
    order_item_id  int GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    drink_order_id int not null,
    drink_id       int not null,
    constraint fk_drinkorderitems_drink_order_id foreign key (drink_order_id)
        references drinkorder (drink_order_id),
    constraint fk_drinkorderitems_drink_id foreign key (drink_id) references drink (drink_id)
);


insert into customer (first_name, middle_initial, last_name)
values ('Bob', 'F', 'Mackey'),
       ('Sharon', 'E', 'Gillian');

set
@bob_id = 1;

set
@sharon_id = 2;

insert into drink (drink_name, drink_cost)
values ('latte', 4.00),
       ('single espresso shot', 1.00),
       ('tea', 2.00),
       ('cappuccino', 3.50);

set
@latte_id = 1;

set
@es_shot_id = 2;

set
@tea_id = 3;

set
@capp_id = 4;


insert into drinkorder (customer_id)
values (@bob_id);

set
@bob_order_id = 1;

insert into drinkorderitems (drink_order_id, drink_id)
values (@bob_order_id, @es_shot_id),
       (@bob_order_id, @capp_id);

update drinkorder
set drink_order_total = (select sum(drink_cost)
                         from drinkorderitems doi
                                  join drink d on doi.drink_id = d.drink_id
                         where drink_order_id = @bob_order_id)
where drink_order_id = @bob_order_id;

insert into drinkorder(customer_id)
values (@sharon_id);

set
@sharon_order_id = 2;

insert into drinkorderitems (drink_order_id, drink_id)
values (@sharon_order_id, @latte_id),
       (@sharon_order_id, @latte_id),
       (@sharon_order_id, @tea_id);

update drinkorder
set drink_order_total = (select sum(drink_cost)
                         from drinkorderitems doi
                                  join drink d on doi.drink_id = d.drink_id
                         where drink_order_id = @sharon_order_id)
where drink_order_id = @sharon_order_id;
