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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import org.eclipse.serializer.concurrency.LockScope;
import org.eclipse.serializer.persistence.types.PersistenceStoring;
import org.eclipse.store.demo.bookstore.BookStoreDemo;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

/**
 * All retail shops operated by this company.
 * <p>
 * This type is used to read and write the {@link Shop}s, their {@link Employee}s and {@link Inventory}s.
 * <p>
 * All operations on this type are thread safe.
 *
 * @see Data#shops()
 * @see LockScope
 */
public class Shops extends LockScope
{
	/**
	 * Simple list to hold the shops.
	 */
	private final List<Shop> shops = new ArrayList<>(1024);

	public Shops()
	{
		super();
	}
	
	/**
	 * Adds a new shop and stores it with the {@link BookStoreDemo}'s {@link EmbeddedStorageManager}.
	 * <p>
	 * This is a synonym for:<pre>this.add(shop, BookStoreDemo.getInstance().storageManager())</pre>
	 *
	 * @param shop the new shop
	 */
	public void add(final Shop shop)
	{
		this.add(shop, BookStoreDemo.getInstance().storageManager());
	}

	/**
	 * Adds a new shop and stores it with the given persister.
	 *
	 * @param shop the new shop
	 * @param persister the persister to store it with
	 * @see #add(Shop)
	 */
	public void add(
		final Shop               shop     ,
		final PersistenceStoring persister
	)
	{
		this.write(() -> {
			this.shops.add(shop);
			persister.store(this.shops);
		});
	}

	/**
	 * Adds a range of new shops and stores it with the {@link BookStoreDemo}'s {@link EmbeddedStorageManager}.
	 * <p>
	 * This is a synonym for:<pre>this.addAll(shops, BookStoreDemo.getInstance().storageManager())</pre>
	 *
	 * @param shops the new shops
	 */
	public void addAll(final Collection<? extends Shop> shops)
	{
		this.addAll(shops, BookStoreDemo.getInstance().storageManager());
	}

	/**
	 * Adds a range of new shops and stores it with the given persister.
	 *
	 * @param shops the new shops
	 * @param persister the persister to store them with
	 * @see #addAll(Collection)
	 */
	public void addAll(
		final Collection<? extends Shop> shops    ,
		final PersistenceStoring         persister
	)
	{
		this.write(() -> {
			this.shops.addAll(shops);
			persister.store(this.shops);
		});
	}

	/**
	 * Gets the total amount of all shops.
	 *
	 * @return the amount of shops
	 */
	public int shopCount()
	{
		return this.read(
			this.shops::size
		);
	}

	/**
	 * Gets all shops as a {@link List}.
	 * Modifications to the returned list are not reflected to the backed data.
	 *
	 * @return all shops
	 */
	public List<Shop> all()
	{
		return this.read(() ->
			new ArrayList<>(this.shops)
		);
	}

	/**
	 * Clears all {@link Lazy} references used by all shops.
	 * This frees the used memory but you do not lose the persisted data. It is loaded again on demand.
	 *
	 * @see Shop#clear()
	 */
	public void clear()
	{
		this.write(() ->
			this.shops.forEach(Shop::clear)
		);
	}

	/**
	 * Executes a function with a {@link Stream} of {@link Shop}s and returns the computed value.
	 *
	 * @param <T> the return type
	 * @param streamFunction computing function
	 * @return the computed result
	 */
	public <T> T compute(final Function<Stream<Shop>, T> streamFunction)
	{
		return this.read(() ->
			streamFunction.apply(
				this.shops.parallelStream()
			)
		);
	}

	/**
	 * Executes a function with a {@link Stream} of {@link InventoryItem}s and returns the computed value.
	 *
	 * @param <T> the return type
	 * @param streamFunction computing function
	 * @return the computed result
	 */
	public <T> T computeInventory(final Function<Stream<InventoryItem>, T> function)
	{
		return this.read(() ->
			function.apply(
				this.shops.parallelStream().flatMap(shop ->
					shop.inventory().compute(entries ->
						entries.map(entry -> new InventoryItem(shop, entry.getKey(), entry.getValue()))
					)
				)
			)
		);
	}

	/**
	 * Gets the shop with a specific name or <code>null</code> if none was found.
	 *
	 * @param name the name to search by
	 * @return the matching shop or <code>null</code>
	 */
	public Shop ofName(final String name)
	{
		return this.read(() ->
			this.shops.stream()
				.filter(shop -> shop.name().equals(name))
				.findAny()
				.orElse(null)
		);
	}

}
