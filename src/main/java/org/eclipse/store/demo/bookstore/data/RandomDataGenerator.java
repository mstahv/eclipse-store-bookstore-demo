package org.eclipse.store.demo.bookstore.data;

/*-
 * #%L
 * EclipseStore BookStore Demo
 * %%
 * Copyright (C) 2023 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.Year;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.money.MonetaryAmount;

import org.eclipse.store.demo.bookstore.BookStoreDemo;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.rapidpm.dependencies.core.logger.HasLogger;

import com.github.javafaker.Faker;

/**
 * Random data generator for the {@link BookStoreDemo}'s {@link Data} root.
 * <p>
 * Data amount boundaries can be controlled with {@link RandomDataAmount}.
 *
 */
public class RandomDataGenerator implements HasLogger
{
	private static class CountryData extends ArrayList<City>
	{
		Faker                     faker ;
		Locale                    locale;
		List<Shop>                shops ;
		Map<City, List<Customer>> people;

		CountryData(
			final Faker  faker ,
			final Locale locale
		)
		{
			super(512);

			this.faker   = faker ;
			this.locale  = locale;

			this.shops = new ArrayList<>();
		}

		City randomCity(final Random random)
		{
			return this.get(random.nextInt(this.size()));
		}

		Customer randomCustomer(final Random random)
		{
			return this.randomCustomer(random, this.randomCity(random));
		}

		Customer randomCustomer(final Random random, final City city)
		{
			final List<Customer> peopleOfCity = this.people.get(city);
			return peopleOfCity.get(random.nextInt(peopleOfCity.size()));
		}

		void dispose()
		{
			this.faker = null;
			this.locale = null;

			this.shops.clear();
			this.shops = null;

			this.people.values().forEach(List::clear);
			this.people.clear();

			this.clear();
		}
	}

	private final Books                  books         ;
	private final Shops                  shops         ;
	private final Customers              customers     ;
	private final Purchases              purchases     ;
	private final RandomDataAmount       dataAmount    ;
	private final EmbeddedStorageManager storageManager;

	private final Random                 random     = new Random()         ;
	private final Faker                  faker      = Faker.instance()     ;
	private final LocalDate              today      = LocalDate.now();
	private final AtomicInteger          customerId = new AtomicInteger(0) ;
	private final Set<String>            usedIsbns  = new HashSet<>(4096)  ;
	private final List<Book>             bookList   = new ArrayList<>(4096);

	private final BigDecimal             minPrice   = new BigDecimal(5)                    ;
	private final BigDecimal             maxPrice   = new BigDecimal(25)                   ;
	private final BigDecimal             priceRange = this.maxPrice.subtract(this.minPrice);

	public RandomDataGenerator(
		final Books                  books         ,
		final Shops                  shops         ,
		final Customers              customers     ,
		final Purchases              purchases     ,
		final RandomDataAmount       dataAmount    ,
		final EmbeddedStorageManager storageManager
	)
	{
		super();

		this.books          = books         ;
		this.shops          = shops         ;
		this.customers      = customers     ;
		this.purchases      = purchases     ;
		this.dataAmount     = dataAmount    ;
		this.storageManager = storageManager;
	}

	public DataMetrics generate()
	{
		final List<Locale> locales = this.supportedLocales();

		this.logger().info("+ " + locales.size() + " locales");

		final List<CountryData> countries = locales.parallelStream()
			.map(this::createCountry)
			.collect(toList());

		this.createBooks(countries);

		this.createShops(countries);

		this.createPurchases(countries);

		final DataMetrics metrics = new DataMetrics(
			this.books.bookCount(),
			countries.size(),
			this.shops.shopCount()
		);

		this.shops.clear();
		this.usedIsbns.clear();
		this.bookList.clear();
		countries.forEach(CountryData::dispose);
		countries.clear();

		System.gc();

		return metrics;
	}

	private List<Locale> supportedLocales()
	{
		final List<Locale> locales = Arrays.asList(
			Locale.US,
			Locale.CANADA_FRENCH,
			Locale.GERMANY,
			Locale.FRANCE,
			Locale.UK,
			new Locale("pt", "BR"),
			new Locale("de", "AT"),
			new Locale("fr", "CH"),
			new Locale("nl", "NL"),
			new Locale("hu", "HU"),
			new Locale("pl", "PL")
		);

		final int maxCountries = this.dataAmount.maxCountries();
		final int max = maxCountries == -1
			? locales.size()
			: maxCountries;
		return max >= locales.size()
			? locales
			: locales.subList(0, max);
	}

	private CountryData createCountry(final Locale locale)
	{
		this.logger().info("> country " + locale.getDisplayCountry());

		final Faker              faker       = Faker.instance(locale);
		final Set<String>        cityNameSet = new HashSet<>();
		final Map<String, State> stateMap    = new HashMap<>();
		final Country            country     = new Country(
			locale.getDisplayCountry(Locale.ENGLISH),
			locale.getCountry()
		);
		final CountryData        countryData = new CountryData(faker, locale);
		this.randomRange(this.dataAmount.maxCitiesPerCountry()).forEach(i ->
		{
			final com.github.javafaker.Address fakerAddress = faker.address();
			final String                       cityName     = fakerAddress.city();
			if(cityNameSet.add(cityName))
			{
				final String stateName = fakerAddress.state();
				final State  state     = stateMap.computeIfAbsent(
					stateName,
					s -> new State(stateName, country)
				);
				countryData.add(new City(cityName, state));
			}
		});

		countryData.people = new HashMap<>(countryData.size(), 1.0f);
		countryData.parallelStream().forEach(city -> {
			countryData.people.put(city, this.createCustomers(countryData, city));
		});

		this.logger().info(
			"+ country " + locale.getDisplayCountry() + " [" + countryData.size() + " cities, " +
				countryData.people.values().stream().mapToInt(List::size).sum() + " customers] "
		);

		return countryData;
	}

	private List<Customer> createCustomers(
		final CountryData countryData,
		final City        city
	)
	{
		return this.randomRange(this.dataAmount.maxCustomersPerCity())
			.mapToObj(i -> new Customer(
				this.customerId.incrementAndGet(),
				countryData.faker.name().fullName(),
				this.createAddress(city, countryData.faker)
			))
			.collect(toList());
	}

	private void createBooks(final List<CountryData> countries)
	{
		final List<Genre> genres = this.createGenres();
		countries.parallelStream().forEach(country ->
		{
			this.logger().info("> books in " + country.locale.getDisplayCountry());

			final List<Publisher> publishers = this.createPublishers(country);
			final List<Author>    authors    = this.createAuthors(country);
			final Language        language   = new Language(country.locale);
			final List<Book>      books      = IntStream.range(0, this.dataAmount.maxBooksPerCountry())
				.mapToObj(i -> country.faker.book().title())
				.map(title -> this.createBook(country, genres, publishers, authors, language, title))
				.collect(toList());

			synchronized(this.bookList)
			{
				this.bookList.addAll(books);
			}

			this.logger().info("+ " + books.size() + " books in "+ country.locale.getDisplayCountry());
		});

		this.books.addAll(this.bookList, this.storageManager);
	}

	private Book createBook(
		final CountryData     country   ,
		final List<Genre>     genres    ,
		final List<Publisher> publishers,
		final List<Author>    authors   ,
		final Language        language  ,
		final String          title
	)
	{
		String isbn;
		synchronized(this.usedIsbns)
		{
			while(!this.usedIsbns.add(isbn = this.faker.code().isbn13(true)))
			{
				// empty loop
			}
		}
		final Genre          genre         = genres.get(this.random.nextInt(genres.size()));
		final Publisher      publisher     = publishers.get(this.random.nextInt(publishers.size()));
		final Author         author        = authors.get(this.random.nextInt(authors.size()));
		final MonetaryAmount purchasePrice = BookStoreDemo.money(this.randomPurchasePrice());
		final MonetaryAmount retailPrice   = BookStoreDemo.retailPrice(purchasePrice);
		return new Book(isbn, title, author, genre, publisher, language, purchasePrice, retailPrice);
	}

	private List<Genre> createGenres()
	{
		return this.randomRange(this.dataAmount.maxGenres())
			.mapToObj(i -> this.faker.book().genre())
			.distinct()
			.map(Genre::new)
			.collect(toList());
	}

	private List<Publisher> createPublishers(final CountryData countryData)
	{
		return this.randomRange(this.dataAmount.maxPublishersPerCountry())
			.mapToObj(i -> countryData.faker.book().publisher())
			.distinct()
			.map(name -> new Publisher(name, this.createAddress(countryData.randomCity(this.random), countryData.faker)))
			.collect(toList());
	}

	private List<Author> createAuthors(final CountryData countryData)
	{
		return this.randomRange(this.dataAmount.maxAuthorsPerCountry())
			.mapToObj(i -> countryData.faker.book().author())
			.distinct()
			.map(name -> new Author(name, this.createAddress(countryData.randomCity(this.random), countryData.faker)))
			.collect(toList());
	}

	private void createShops(final List<CountryData> countries)
	{
		countries.parallelStream().forEach(country ->
		{
			this.logger().info("> shops in " + country.locale.getDisplayCountry());

			country.forEach(
				city -> this.randomRange(this.dataAmount.maxShopsPerCity()).forEach(
					i -> country.shops.add(this.createShop(countries, country, city, i))
				)
			);

			this.logger().info("+ " + country.shops.size() + " shops in " + country.locale.getDisplayCountry());
		});

		countries.forEach(country -> this.shops.addAll(country.shops, this.storageManager));
	}

	private Shop createShop(
		final List<CountryData> countries  ,
		final CountryData       countryData,
		final City              city       ,
		final int               nr
	)
	{
		final String             name      = city.name() + " Shop " + nr;
		final Address            address   = this.createAddress(city, countryData.faker);
		final List<Employee>     employees = this.createEmployees(countryData, city);
		final Map<Book, Integer> inventory = this.randomRange(this.dataAmount.maxBooksPerShop())
			.mapToObj(i -> this.randomBook())
			.distinct()
			.collect(toMap(
				book -> book,
				book -> this.random.nextInt(50) + 1
			));
		return new Shop(name, address, employees, new Inventory(inventory));
	}

	private void createPurchases(final List<CountryData> countries)
	{
		final Set<Customer> customers = new HashSet<>(4096);

		final int           thisYear  = Year.now().getValue();
		final int           startYear = thisYear - this.randomMax(this.dataAmount.maxAgeOfShopsInYears()) + 1;
		IntStream.rangeClosed(startYear, thisYear).forEach(
			year -> this.createPurchases(countries, year, customers)
		);

		this.customers.addAll(customers, this.storageManager);
		customers.clear();
	}

	private void createPurchases(
		final List<CountryData> countries,
		final int               year     ,
		final Set<Customer>     customers
	)
	{
		this.logger().info("> purchases in " + year);

		final List<Purchase> purchases = countries.parallelStream()
			.flatMap(
				country -> country.shops.stream().flatMap(
					shop -> this.createPurchases(country, year, shop)
				)
			)
			.collect(toList());

		final Set<Customer> customersForYear = this.purchases.init(year, purchases, this.storageManager);

		this.logger().info("+ " + purchases.size() + " purchases in " + year);

		customers.addAll(customersForYear);

		customersForYear.clear();
		purchases.clear();

		System.gc();
	}

	private Stream<Purchase> createPurchases(
		final CountryData countryData,
		final int         year       ,
		final Shop        shop
	)
	{
		final List<Book>     books      = shop.inventory().books();
		final boolean        isLeapYear = Year.of(year).isLeap();
		final Random         random     = this.random;
		return shop.employees().flatMap(employee ->
			this.randomRange(this.dataAmount.maxPurchasesPerEmployeePerYear()).mapToObj(pi -> {
				final Customer customer = pi % 10 == 0
					? countryData.randomCustomer(random)
					: countryData.randomCustomer(random, shop.address().city());
				final LocalDateTime timestamp = this.randomDateTime(year, isLeapYear, random);
				final List<PurchaseItem> items = this.randomRange(this.dataAmount.maxItemsPerPurchase())
					.mapToObj(ii -> new PurchaseItem(books.get(random.nextInt(books.size())), random.nextInt(3) + 1))
					.collect(toList());
				return new Purchase(shop, employee, customer, timestamp, items);
			})
		);
	}

	private LocalDateTime randomDateTime(
		final int     year      ,
		final boolean isLeapYear,
		final Random  random
	)
	{
		Month month;
		int   dayOfMonth;
		
		if(year == this.today.getYear())
		{
			month      = Month.of(random.nextInt(this.today.getMonthValue()) + 1);
			dayOfMonth = random.nextInt(month.length(isLeapYear)) + 1;
			if(month == this.today.getMonth() && dayOfMonth >= this.today.getDayOfMonth())
			{
				dayOfMonth = this.today.getDayOfMonth() - 1;
			}
		}
		else
		{
			month      = Month.of(random.nextInt(12) + 1);
			dayOfMonth = random.nextInt(month.length(isLeapYear)) + 1;
		}
		
		final int   hour       = 8 + random.nextInt(11);
		final int   minute     = random.nextInt(60);
		final int   second     = random.nextInt(60);
		return LocalDateTime.of(year, month.getValue(), dayOfMonth, hour, minute, second);
	}

	private Book randomBook()
	{
		return this.bookList.get(this.random.nextInt(this.bookList.size()));
	}

	private List<Employee> createEmployees(
		final CountryData countryData,
		final City        city
	)
	{
		return this.randomRange(this.dataAmount.maxEmployeesPerShop())
			.mapToObj(i -> new Employee(
				countryData.faker.name().fullName(),
				this.createAddress(city, countryData.faker)
			))
			.collect(toList());
	}

	private Address createAddress(
		final City  city ,
		final Faker faker
	)
	{
		final com.github.javafaker.Address fa = faker.address();
		return new Address(
			fa.streetAddress(),
			fa.secondaryAddress(),
			fa.zipCode(),
			city
		);
	}

	private BigDecimal randomPurchasePrice()
	{
		return this.minPrice
			.add(new BigDecimal(Math.random()).multiply(this.priceRange));
	}

	private IntStream randomRange(final int upperBoundInclusive)
	{
		return IntStream.rangeClosed(0, this.randomMax(upperBoundInclusive));
	}

	private int randomMax(final int upperBoundInclusive)
	{
		int max = this.random.nextInt(upperBoundInclusive);
		final double minRatio;
		if((minRatio = this.dataAmount.minRatio()) > 0)
		{
			max = Math.max(max, (int)(upperBoundInclusive * minRatio));
		}
		return max;
	}

}
