package net.tomp2p.rpc;
import java.util.Random;

import junit.framework.Assert;

import net.tomp2p.Utils2;
import net.tomp2p.futures.FutureResponse;
import net.tomp2p.p2p.Peer;
import net.tomp2p.peers.Number160;
import net.tomp2p.peers.Number320;
import net.tomp2p.peers.PeerAddress;
import net.tomp2p.storage.Data;
import net.tomp2p.storage.TrackerStorage;
import net.tomp2p.utils.Utils;

import org.junit.Test;


public class TestTracker
{
	final static Random rnd = new Random(0);

	@Test
	public void testTrackerPut() throws Exception
	{
		Peer sender = null;
		Peer recv1 = null;
		try
		{
			sender = new Peer(55, new Number160("0x50"));
			sender.listen(2424, 2424);
			recv1 = new Peer(55, new Number160("0x20"));
			recv1.listen(8088, 8088);
			Number160 loc = new Number160(rnd);
			Number160 dom = new Number160(rnd);
			// make a good guess based on the config and the maxium tracker that can be found
			SimpleBloomFilter<Number160> bloomFilter=new SimpleBloomFilter<Number160>(4096, 1000);
			FutureResponse fr = sender.getTrackerRPC().addToTracker(recv1.getPeerAddress(), loc,
					dom, new Data(new String("data")), false, false, bloomFilter);
			fr.awaitUninterruptibly();
			Assert.assertEquals(true, fr.isSuccess());
			bloomFilter=new SimpleBloomFilter<Number160>(4096, 1000);
			fr = sender.getTrackerRPC().getFromTracker(recv1.getPeerAddress(), loc, dom, false,
					false, bloomFilter);
			fr.awaitUninterruptibly();
			System.err.println(fr.getFailedReason());
			Assert.assertEquals(true, fr.isSuccess());
			PeerAddress peerAddress=fr.getResponse().getDataMap().values().iterator().next().getPeerAddress();
			Assert.assertEquals(sender.getPeerAddress(), peerAddress);
		}
		finally
		{
			if (sender != null)
				sender.shutdown();
			if (recv1 != null)
				recv1.shutdown();
		}
	}

	@Test
	public void testTrackerMap() throws Exception
	{
		TrackerStorage ts = new TrackerStorage(42L);
		ts.setTrackerTimoutSeconds(1);
		Number160 loc = new Number160(rnd);
		Number160 dom = new Number160(rnd);
		PeerAddress pa1 = Utils2.createAddress("0x1");
		PeerAddress pa2 = Utils2.createAddress("0x2");
		Data data1 = new Data(new String("data"));
		data1.setTTLSeconds(1);
		data1.setPeerAddress(pa1);
		ts.put(loc, dom, null, data1);
		//
		Data data2 = new Data(new String("data"));
		data2.setTTLSeconds(1);
		data2.setPeerAddress(pa2);
		ts.put(loc, dom, null, data2);
		Utils.sleep(500);
		Assert.assertEquals(2, ts.get(new Number320(loc, dom)).size());
		Utils.sleep(600);
		Assert.assertEquals(0, ts.get(new Number320(loc, dom)).size());
	}
	
	@Test
	public void testTrackerBloomFilter() throws Exception
	{
		Peer sender = null;
		Peer recv1 = null;
		try
		{
			sender = new Peer(55, new Number160("0x50"));
			sender.listen(2424, 2424);
			recv1 = new Peer(55, new Number160("0x20"));
			recv1.listen(8088, 8088);
			Number160 loc = new Number160(rnd);
			Number160 dom = new Number160(rnd);
			// make a good guess based on the config and the maxium tracker that can be found
			SimpleBloomFilter<Number160> bloomFilter=new SimpleBloomFilter<Number160>(4096, 1000);
			FutureResponse fr = sender.getTrackerRPC().addToTracker(recv1.getPeerAddress(), loc,
					dom, new Data(new String("data")), false, false, bloomFilter);
			fr.awaitUninterruptibly();
			Assert.assertEquals(true, fr.isSuccess());
			bloomFilter.add(sender.getPeerID());
			fr = sender.getTrackerRPC().getFromTracker(recv1.getPeerAddress(), loc, dom, false,
					false, bloomFilter);
			fr.awaitUninterruptibly();
			System.err.println(fr.getFailedReason());
			Assert.assertEquals(true, fr.isSuccess());
			Assert.assertEquals(0, fr.getResponse().getDataMap().size());
		}
		finally
		{
			if (sender != null)
				sender.shutdown();
			if (recv1 != null)
				recv1.shutdown();
		}
	}
}