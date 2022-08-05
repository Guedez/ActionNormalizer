package br.com.guedez.wurm.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.gotti.wurmunlimited.modloader.classhooks.HookException;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.classhooks.InvocationHandlerFactory;
import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.Initable;
import org.gotti.wurmunlimited.modloader.interfaces.PlayerMessageListener;
import org.gotti.wurmunlimited.modloader.interfaces.ServerPollListener;
import org.gotti.wurmunlimited.modloader.interfaces.ServerShutdownListener;
import org.gotti.wurmunlimited.modloader.interfaces.ServerStartedListener;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;

import com.google.common.io.Files;
import com.wurmonline.server.Server;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.ActionStack;
import com.wurmonline.server.behaviours.NoSuchActionException;
import com.wurmonline.server.creatures.Communicator;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.players.Player;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtPrimitiveType;
import javassist.bytecode.Descriptor;

public class ActionNormalizer implements WurmServerMod, Configurable, Initable, ServerShutdownListener, ServerStartedListener, ServerPollListener, PlayerMessageListener {
	public static long baseline = 0;
	public static float multiplier = 0;
	public static float max_multiplier = 0;
	public static float min_multiplier = 0;
	public static long startingTime = 0;
	public static int min_admin_power = 0;
	public static long autosave_interval = 0;
	public static long autosave_backups = 0;
	public static long _autosave_permanent_backups_interval = 0;
	public static long autosave_permanent_backups_interval = 0;
	public static HashSet<Integer> exceptions;
	public static HashSet<Integer> longactionfix;
	public static HashSet<Integer> settimefix;

	public static float penalty = 0;

	public static int uptime_cap1 = 0;
	public static float uptime_mult1 = 0;
	public static int uptime_cap2 = 0;
	public static float uptime_mult2 = 0;
	public static int uptime_cap3 = 0;
	public static float uptime_mult3 = 0;

	public static float rare_per_day = 0;
	public static boolean ensure_rare = false;
	public Logger logger = Logger.getLogger(this.getClass().getName());

	@Override
	public void configure(Properties properties) {
		baseline = Long.valueOf(properties.getProperty("baseline", "86400000"));// 7200000=two hours in milisecs
		startingTime = Long.valueOf(properties.getProperty("startingTime", "43200000"));
		autosave_interval = Long.valueOf(properties.getProperty("autosave_interval", "300000"));
		autosave_backups = Long.valueOf(properties.getProperty("autosave_backups", "15"));
		autosave_permanent_backups_interval = Long.valueOf(properties.getProperty("autosave_permanent_backups_interval", "5"));
		multiplier = Float.valueOf(properties.getProperty("multiplier", "12.0"));
		penalty = Float.valueOf(properties.getProperty("penalty", "4.0"));
		max_multiplier = Float.valueOf(properties.getProperty("max_multiplier", "15.0"));
		min_multiplier = Float.valueOf(properties.getProperty("min_multiplier", "0.5"));
		ActionNormalizer.exceptions = new HashSet<Integer>(arrayprop(properties, "exceptions"));
		ActionNormalizer.longactionfix = new HashSet<Integer>(arrayprop(properties, "longactionfix"));
		ActionNormalizer.settimefix = new HashSet<Integer>(arrayprop(properties, "settimefix"));
		min_admin_power = Integer.valueOf(properties.getProperty("min_admin_power", "5"));

		uptime_cap1 = Integer.valueOf(properties.getProperty("uptime_cap1", "0"));
		uptime_mult1 = Float.valueOf(properties.getProperty("uptime_mult1", "0"));
		uptime_cap2 = Integer.valueOf(properties.getProperty("uptime_cap2", "0"));
		uptime_mult2 = Float.valueOf(properties.getProperty("uptime_mult2", "0"));
		uptime_cap3 = Integer.valueOf(properties.getProperty("uptime_cap3", "0"));
		uptime_mult3 = Float.valueOf(properties.getProperty("uptime_mult3", "0"));

		rare_per_day = Float.valueOf(properties.getProperty("rare_per_day", "6.0"));
		ensure_rare = Boolean.valueOf(properties.getProperty("ensure_rare", "true"));
	}

	private Collection<? extends Integer> arrayprop(Properties properties, String prop) {
		String exceptions = properties.getProperty(prop, "");
		LinkedList<Integer> exc = new LinkedList<Integer>();
		for (String s : exceptions.split(",")) {
			if (!s.isEmpty())
				exc.add(Integer.parseInt(s));
		}
		return exc;
	}

	public void init() {
		if (baseline > 0 && multiplier > 0) {
			try {// Creature performer, Skill skill, @Nullable Item source, double bonus
				CtClass CString = ClassPool.getDefault().getCtClass("java.lang.String");
				CtClass cCreature = ClassPool.getDefault().getCtClass("com.wurmonline.server.creatures.Creature");
				CtClass cAction = ClassPool.getDefault().getCtClass("com.wurmonline.server.behaviours.Action");
				CtClass cskill = ClassPool.getDefault().getCtClass("com.wurmonline.server.skills.Skill");
				CtClass citem = ClassPool.getDefault().getCtClass("com.wurmonline.server.items.Item");
				CtClass cCreationEntry = ClassPool.getDefault().getCtClass("com.wurmonline.server.items.CreationEntry");
				CtClass cRecipe = ClassPool.getDefault().getCtClass("com.wurmonline.server.items.Recipe");

				HookManager.getInstance().registerHook("com.wurmonline.server.behaviours.Actions", "getQuickActionTime", Descriptor.ofMethod(CtPrimitiveType.intType, new CtClass[] { cCreature, cskill, citem, CtPrimitiveType.doubleType, }), new ChangeActionSpeedCreatureIndex(0));

				HookManager.getInstance().registerHook("com.wurmonline.server.behaviours.Actions", "getImproveActionTime", Descriptor.ofMethod(CtPrimitiveType.intType, new CtClass[] { cCreature, citem }), new ChangeActionSpeedCreatureIndex(0));

				HookManager.getInstance().registerHook("com.wurmonline.server.behaviours.Actions", "getRepairActionTime", Descriptor.ofMethod(CtPrimitiveType.intType, new CtClass[] { cCreature, cskill, CtPrimitiveType.doubleType }), new ChangeActionSpeedCreatureIndex(0));

				HookManager.getInstance().registerHook("com.wurmonline.server.behaviours.Actions", "getItemCreationTime", Descriptor.ofMethod(CtPrimitiveType.intType, new CtClass[] { //
						CtPrimitiveType.intType, cCreature, cskill, cCreationEntry, citem, citem, CtPrimitiveType.booleanType, }), new ChangeActionSpeedCreatureIndex(1));

				HookManager.getInstance().registerHook("com.wurmonline.server.behaviours.Actions", "getRecipeCreationTime", Descriptor.ofMethod(CtPrimitiveType.intType, new CtClass[] { //
						CtPrimitiveType.intType, cCreature, cskill, cRecipe, citem, citem, CtPrimitiveType.booleanType, }), new ChangeActionSpeedCreatureIndex(1));

				HookManager.getInstance().registerHook("com.wurmonline.server.behaviours.Actions", "getStandardActionTime", Descriptor.ofMethod(CtPrimitiveType.intType, new CtClass[] { //
						cCreature, cskill, citem, CtPrimitiveType.doubleType }), new ChangeActionSpeedCreatureIndex(0));

				HookManager.getInstance().registerHook("com.wurmonline.server.behaviours.Actions", "getVariableActionTime", Descriptor.ofMethod(CtPrimitiveType.intType, new CtClass[] { //
						cCreature, cskill, citem, CtPrimitiveType.doubleType, CtPrimitiveType.intType, CtPrimitiveType.intType, CtPrimitiveType.intType }), new ChangeActionSpeedCreatureIndex(0));

				HookManager.getInstance().registerHook("com.wurmonline.server.behaviours.Actions", "getSlowActionTime", Descriptor.ofMethod(CtPrimitiveType.intType, new CtClass[] { //
						cCreature, cskill, citem, CtPrimitiveType.doubleType }), new ChangeActionSpeedCreatureIndex(0));

				HookManager.getInstance().registerHook("com.wurmonline.server.behaviours.ActionStack", "poll", Descriptor.ofMethod(CtPrimitiveType.booleanType, new CtClass[] { cCreature }), new ActionPooler());

				HookManager.getInstance().registerHook("com.wurmonline.server.behaviours.Action", "setTimeLeft", Descriptor.ofMethod(CtPrimitiveType.voidType, new CtClass[] { CtPrimitiveType.intType }), new ActionSetTime());

				HookManager.getInstance().registerHook("com.wurmonline.server.players.Player", "sendActionControl", Descriptor.ofMethod(CtPrimitiveType.voidType, new CtClass[] { CString, CtPrimitiveType.booleanType, CtPrimitiveType.intType }), new ActionControl());

				HookManager.getInstance().registerHook("com.wurmonline.server.players.Player", "poll", Descriptor.ofMethod(CtPrimitiveType.booleanType, new CtClass[] {}), new PlayerPooler());

				HookManager.getInstance().registerHook("com.wurmonline.server.players.Player", "getRarity", Descriptor.ofMethod(CtPrimitiveType.byteType, new CtClass[] {}), new PlayerGetRarity());

				HookManager.getInstance().registerHook("com.wurmonline.server.behaviours.Action", "poll", Descriptor.ofMethod(CtPrimitiveType.booleanType, new CtClass[] {}), new ActionPool());

			} catch (Exception e) {
				throw new HookException(e);
			}
		} else {
//			logger.log(Level.INFO, "Anti bot missconfigured, max_hours:" + max_hours + " reset:" + reset);
		}
	}

	public void save() {
		for (int i = (int) autosave_backups - 1; i > 0; i--) {
			File from = new File("./mods/ActionNormalizer/Save" + i + ".txt");
			File to = new File("./mods/ActionNormalizer/Save" + (i + 1) + ".txt");
			if (from.exists()) {
				try {
					Files.copy(from, to);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		File from = new File("./mods/ActionNormalizer/Save.txt");
		File to = new File("./mods/ActionNormalizer/Save1.txt");
		if (from.exists()) {
			try {
				Files.copy(from, to);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		File file = new File("./mods/ActionNormalizer/Save.txt");
		file.getParentFile().mkdirs();
		if (_autosave_permanent_backups_interval++ >= autosave_permanent_backups_interval) {
			_autosave_permanent_backups_interval = 0;
			File file2 = new File("./mods/ActionNormalizer/" + System.currentTimeMillis() + ".txt");
			SaveToFile(file2);
		}
		SaveToFile(file);
	}

	private void SaveToFile(File file) {
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
			bw.write(System.currentTimeMillis() + "\n");
			bw.write(GetServerUptime() + "\n");
			for (Map.Entry<String, NormalizerParams> params : ParamMap.entrySet()) {
				bw.write(params.getKey() + ";" + params.getValue().save() + "\n");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private long _loadedAt;
	private long _totalserveruptime;

	public long GetServerUptime() {
		return _totalserveruptime + (System.currentTimeMillis() - _loadedAt);
	}

	public void load() {
		ParamMap.clear();
		_loadedAt = System.currentTimeMillis();
		File file = new File("./mods/ActionNormalizer/Save.txt");
		file.getParentFile().mkdirs();
		if (file.exists()) {
			try (BufferedReader br = new BufferedReader(new FileReader(file))) {
				String line = br.readLine();
				if (line == null) {
					return;
				}
				long ServerOffFor = _loadedAt - Long.parseLong(line);
				line = br.readLine();
				if (line == null) {
					return;
				}
				_totalserveruptime = Long.parseLong(line);
				logger.log(Level.INFO, "Server was offline for " + Server.getTimeFor(ServerOffFor));
				while ((line = br.readLine()) != null) {
					String[] split = line.split(";");
					ParamMap.put(split[0], new NormalizerParams(split[1], ServerOffFor));
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public int GetModTime(Creature performer, int defaultTIme) {
		if (performer.isPlayer()) {
			if (((Player) performer).getEnemyPresense() > 0) {
				return defaultTIme;
			}
			int orig = defaultTIme;
			defaultTIme *= penalty;
			NormalizerParams NormalizerParams = GetParams(performer);
			float multiplier = NormalizerParams.Multiplier();
			int Time = Math.max(1, (int) (defaultTIme / multiplier));
			int dif = orig - Time;
			NormalizerParams.Expend(Math.max(0, dif) * 100);
			ExpendSleep(performer, dif);
			return Time;
		}
		return defaultTIme;
	}

	private void ExpendSleep(Creature performer, int dif) {
		Player player = (Player) performer;
		if (player.hasSleepBonus()) {
			float dd = dif / 10f;
			dif /= 10;
			player.getSaveFile().sleep -= dif;
			if (Math.random() < dd) {
				player.getSaveFile().sleep--;
			}
			player.getCommunicator().sendSleepInfo();
		}
	}

	public void Expend(Creature performer, long defaultTIme) {
		if (performer.isPlayer() && defaultTIme > 0 && defaultTIme < 100000) {
			NormalizerParams NormalizerParams = GetParams(performer);
			NormalizerParams.Expend(defaultTIme);
			ExpendSleep(performer, (int) (defaultTIme / 100));
		}
	}

	private NormalizerParams GetParams(Creature performer) {
		NormalizerParams NormalizerParams;
		if (!ParamMap.containsKey(performer.getName())) {
			NormalizerParams = new NormalizerParams(System.currentTimeMillis());
			ParamMap.put(performer.getName(), NormalizerParams);
		} else {
			NormalizerParams = ParamMap.get(performer.getName());
		}
		return NormalizerParams;
	}

	private NormalizerParams GetParams(Communicator performer) {
		NormalizerParams NormalizerParams;
		if (!ParamMap.containsKey(performer.player.getName())) {
			NormalizerParams = new NormalizerParams(System.currentTimeMillis());
			ParamMap.put(performer.player.getName(), NormalizerParams);
		} else {
			NormalizerParams = ParamMap.get(performer.player.getName());
		}
		return NormalizerParams;
	}

	private NormalizerParams GetParams(String name) {
		NormalizerParams NormalizerParams;
		if (!ParamMap.containsKey(name)) {
			return null;
		} else {
			NormalizerParams = ParamMap.get(name);
		}
		return NormalizerParams;
	}

	public float GetMultiplier(Creature performer) {
		if (performer.isPlayer()) {
			if (((Player) performer).getEnemyPresense() > 0) {
				return 1;
			}
			NormalizerParams NormalizerParams = GetParams(performer);
			return NormalizerParams.Multiplier();
		}
		return 1;
	}

	public class ChangeActionSpeedCreatureIndex implements InvocationHandlerFactory {
		public ChangeActionSpeedCreatureIndex(int index) {
			creatureIndex = index;
		}

		int creatureIndex;

		public class ChangeActionSpeedQuickActionTimeHandler implements InvocationHandler {
			@Override
			public Object invoke(Object object, Method method, Object[] args) throws Throwable {
				int deftime = (int) method.invoke(object, args);
				Creature c = (Creature) args[creatureIndex];
				try {
					if (exceptions.contains(new Integer(c.getActions().getCurrentAction().getNumber()))) {
						return deftime;
					}
				} catch (Exception e) {
				}
				return GetModTime(c, deftime);
			}

		}

		public InvocationHandler createInvocationHandler() {
			return new ChangeActionSpeedQuickActionTimeHandler();
		}
	}

	public class NormalizerParams {
		public NormalizerParams(long now) {
			LastChecked = now;
			long uptimesecs = (GetServerUptime() / 1000);
			long acc = 0;
			acc += Long.min(uptime_cap1 * 60, Long.max(0, uptimesecs)) * uptime_mult1;
			uptimesecs -= uptime_cap1 * 60;
			acc += Long.min(uptime_cap2 * 60, Long.max(0, uptimesecs)) * uptime_mult2;
			uptimesecs -= uptime_cap2 * 60;
			acc += Long.min(uptime_cap3 * 60, Long.max(0, uptimesecs)) * uptime_mult3;
			uptimesecs -= uptime_cap3 * 60;
			Accumulated = startingTime + acc * 1000;
		}

		public NormalizerParams(String string, long ServerOffFor) {
			String[] split = string.split(",");
			LastChecked = Long.parseLong(split[0]) + ServerOffFor;
			Accumulated = Long.parseLong(split[1]);
		}

		public String save() {
			return LastChecked + "," + Accumulated;
		}

		public long LastChecked;
		public long Accumulated;
		public int window;

		public void Add(long now) {
			if (LastChecked < now) {
				Accumulated += now - LastChecked;
				LastChecked = now;
			}
		}

		public void SetCheck(long now) {
			LastChecked = now;
		}

		public float Multiplier() {
			float mult = Math.max(Math.min(max_multiplier, Accumulated / (baseline * 1f) * multiplier), min_multiplier);
			return mult;
		}

		public void Expend(long Time) {
			Accumulated -= (long) (Time / penalty);
		}
	}

	public HashMap<String, NormalizerParams> ParamMap = new HashMap<String, NormalizerParams>();

	public class ActionPooler implements InvocationHandlerFactory {
		public class ActionPoolerHandler implements InvocationHandler {
			@Override
			public Object invoke(Object object, Method method, Object[] args) throws Throwable {
				Creature c = (Creature) args[0];
				if (c.isPlayer()) {
					long now = System.currentTimeMillis();
					NormalizerParams NormalizerParams;
					if (!ParamMap.containsKey(c.getName())) {
						NormalizerParams = new NormalizerParams(now);
						ParamMap.put(c.getName(), NormalizerParams);
					} else {
						NormalizerParams = ParamMap.get(c.getName());
					}

					ActionStack stack = (ActionStack) object;
					try {
						stack.getCurrentAction().getNumber();
						NormalizerParams.SetCheck(now);
					} catch (NoSuchActionException e) {
						NormalizerParams.Add(now);
					}
				}
				return method.invoke(object, args);
			}
		}

		public InvocationHandler createInvocationHandler() {
			return new ActionPoolerHandler();
		}
	}

	public class ActionSetTime implements InvocationHandlerFactory {
		public class ActionSetTimeHandler implements InvocationHandler {
			@Override
			public Object invoke(Object object, Method method, Object[] args) throws Throwable {
				Action c = (Action) object;
				if (c.getPerformer().isPlayer()) {
					short Number = c.getNumber();
					if (settimefix.contains(new Integer(Number))) {
						int timeLeft = (int) ((int) args[0] * penalty);
						int getModTime = GetModTime(c.getPerformer(), timeLeft);
						args[0] = getModTime;
						NewTimer.put(c.getPerformer(), getModTime);
					}
				}
				return method.invoke(object, args);
			}
		}

		public InvocationHandler createInvocationHandler() {
			return new ActionSetTimeHandler();
		}
	}

	private HashMap<Creature, Integer> NewTimer = new HashMap<Creature, Integer>();

	public class ActionControl implements InvocationHandlerFactory {
		public class ActionControlHandler implements InvocationHandler {
			@Override
			public Object invoke(Object object, Method method, Object[] args) throws Throwable {
				Creature c = (Creature) object;
				if (c.isPlayer()) {
					if (NewTimer.containsKey(c)) {
						args[2] = NewTimer.get(c);
						NewTimer.remove(c);
					}
				}
				return method.invoke(object, args);
			}
		}

		public InvocationHandler createInvocationHandler() {
			return new ActionControlHandler();
		}
	}

	Field _lastPolledAction;

	public class ActionPool implements InvocationHandlerFactory {
		public class ActionPoolHandler implements InvocationHandler {
			@Override
			public Object invoke(Object object, Method method, Object[] args) throws Throwable {
				Action c = (Action) object;
				if (c.getPerformer().isPlayer()) {// lastPolledAction
					float mult = GetMultiplier(c.getPerformer());
					if (mult == 1) {
						return method.invoke(object, args);
					}
					short Number = c.getNumber();
					if (longactionfix.contains(new Integer(Number))) {
						if (_lastPolledAction == null) {
							_lastPolledAction = c.getClass().getDeclaredField("lastPolledAction");
							_lastPolledAction.setAccessible(true);
						}
						int mr = ((int) (mult / penalty) - 1);
						mult -= mr;
						long current = System.currentTimeMillis();
						long consumed = 0;
						long lastPolledAction = _lastPolledAction.getLong(object);
						for (int i = 0; i < mr; i++) {
							method.invoke(object, args);
							_lastPolledAction.setLong(object, lastPolledAction);
							consumed += current - lastPolledAction;
						}
						if (Math.random() < mult) {
							method.invoke(object, args);
							_lastPolledAction.setLong(object, lastPolledAction);
							consumed += current - lastPolledAction;
						}
						Expend(c.getPerformer(), consumed);
					}
				}
				return method.invoke(object, args);
			}
		}

		public InvocationHandler createInvocationHandler() {
			return new ActionPoolHandler();
		}
	}

	public class PlayerPooler implements InvocationHandlerFactory {
		public class PlayerPoolerHandler implements InvocationHandler {
			@Override
			public Object invoke(Object object, Method method, Object[] args) throws Throwable {
				Player p = (Player) object;
				NormalizerParams NormalizerParams = GetParams(p);
				if (NormalizerParams.window >= 0) {
					NormalizerParams.window--;
				} else if (Server.rand.nextFloat() * (86400 / rare_per_day) <= NormalizerParams.Multiplier()) {
					NormalizerParams.window = 20;
					if (p.getCitizenVillage() != null)
						NormalizerParams.window += (int) Math.min(10.0F, p.getCitizenVillage().getFaithCreateValue());
				}
				return method.invoke(object, args);
			}
		}

		public InvocationHandler createInvocationHandler() {
			return new PlayerPoolerHandler();
		}
	}

	public class PlayerGetRarity implements InvocationHandlerFactory {
		public class GetRarityHandler implements InvocationHandler {
			@Override
			public Object invoke(Object object, Method method, Object[] args) throws Throwable {
				Player p = (Player) object;
				NormalizerParams NormalizerParams = GetParams(p);
				byte rarity = 0;
				if (NormalizerParams.window > 0) {
					if (Server.rand.nextFloat() > 1 / NormalizerParams.Multiplier()) {
						return 0;
					}
					NormalizerParams.window = 0;
					float faintChance = 1.03F;
					int supPremModifier = 3;
					if (Server.rand.nextFloat() * 10000.0F <= faintChance) {
						rarity = 3;
					} else if (Server.rand.nextInt(100) <= 0 + supPremModifier) {
						rarity = 2;
					} else if (ensure_rare || Server.rand.nextBoolean()) {
						rarity = 1;
					}
					return rarity;
				}
				return rarity;
			}
		}

		public InvocationHandler createInvocationHandler() {
			return new GetRarityHandler();
		}
	}

	@Override
	public void onServerStarted() {
		logger.log(Level.INFO, "ActionNormalizer set for multiplier of " + multiplier + " at " + Server.getTimeFor(baseline) + " saved up time.");
		logger.log(Level.INFO, "max_multiplier: " + max_multiplier);
		logger.log(Level.INFO, "min_multiplier: " + min_multiplier);
		logger.log(Level.INFO, "rare_per_day: " + rare_per_day);
		logger.log(Level.INFO, "Players start with " + Server.getTimeFor(startingTime) + " of saved time");
		logger.log(Level.INFO, "Auto save every " + Server.getTimeFor(autosave_interval) + ". Keeping up to " + autosave_backups + " files.");

		load();
	}

	@Override
	public void onServerShutdown() {
		save();
	}

	private long LASTSAVE = System.currentTimeMillis();

	@Override
	public void onServerPoll() {
		if (System.currentTimeMillis() - LASTSAVE > autosave_interval) {
			LASTSAVE = System.currentTimeMillis();
			logger.log(Level.INFO, "Autosaving");
			save();
		}
	}

	@Override
	public boolean onPlayerMessage(Communicator p0, String p1) {
		return ManageCommunication(p0, p1);
	}

	private boolean ManageCommunication(Communicator p0, String p1) {
		if (p1.equalsIgnoreCase("/aninfo")) {
			try {
				NormalizerParams NormalizerParams = GetParams(p0);
				p0.sendNormalServerMessage("Multiplier: " + String.format("%.2f", NormalizerParams.Multiplier()) + ", Saved up time: " + Server.getTimeFor(NormalizerParams.Accumulated) + " " + (NormalizerParams.Accumulated % 60000 / 1000) + " seconds.");
				return true;
			} catch (Exception e) {
				e.printStackTrace();
				p0.sendNormalServerMessage("Unknown server error, please contact the admins");
				return true;
			}
		}
		if (p0.getPlayer().getPower() >= min_admin_power) {
			if (p1.toLowerCase().startsWith("/angive")) {
				String[] split = p1.split(" ");
				NormalizerParams NormalizerParams = GetParams(split[1]);
				if (NormalizerParams != null) {
					NormalizerParams.Accumulated += Long.parseLong(split[2]);
					p0.sendNormalServerMessage("Added " + Server.getTimeFor(Long.parseLong(split[2])) + " " + (Long.parseLong(split[2]) % 60000 / 1000) + " seconds time for " + split[1]);
					return true;
				}
				p0.sendNormalServerMessage("Player " + split[1] + " not found!");
				return true;
			}
		}
		return false;
	}
}