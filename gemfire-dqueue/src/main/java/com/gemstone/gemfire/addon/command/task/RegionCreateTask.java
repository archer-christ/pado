package com.gemstone.gemfire.addon.command.task;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import com.gemstone.gemfire.DataSerializable;
import com.gemstone.gemfire.DataSerializer;
import com.gemstone.gemfire.addon.command.CommandResults;
import com.gemstone.gemfire.addon.command.CommandTask;
import com.gemstone.gemfire.addon.command.task.data.MemberInfo;
import com.gemstone.gemfire.addon.command.task.data.RegionAttributeInfo;
import com.gemstone.gemfire.addon.util.DataSerializerEx;
import com.gemstone.gemfire.addon.util.RegionUtil;
import com.gemstone.gemfire.cache.AttributesFactory;
import com.gemstone.gemfire.cache.Cache;
import com.gemstone.gemfire.cache.CacheException;
import com.gemstone.gemfire.cache.CacheFactory;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.distributed.DistributedMember;

/**
 * 
 * RegionCreateTask creates a remote region. Invoke this class using
 * the function service as follows:
 * <p>
 * <pre>
 * String regionPath = "/foo";
 * RegionAttributeInfo attrInfo = new RegionAttributeInfo();
 * 		attrInfo.setAttribute(RegionAttributeInfo.DATA_POLICY, DataPolicy.REPLICATE.toString());
 * RegionCreateTask task = new RegionCreateTask(regionPath, attrInfo);
 * 		
 * ResultCollector collector = FunctionService.onServers(pool)
 * 			.withArgs(task).execute("CommandTaskFunction");
 * List resultList = (List)collector.getResult();
 * </pre>
 * <p>
 * Check CommandResults.getCode() to see the task execution status as follows:
 * <p>
 * <pre>
 * List<CommandResults> resultList = (List<CommandResults>)collector.getResult();
 * for (CommandResults commandResults : resultList) {
 *     System.out.println("code=" + commandResults.getCode() + ", message=" + commandResults.getCodeMessage());
 * }
 * </pre>
 * <p>
 * RegionCreateTask.SUCCESS_CREATED for region successfully created,
 * RegionCreateTask.ERROR_... for an error creating region.
 * 
 * @author dpark
 * 
 */
public class RegionCreateTask implements CommandTask, DataSerializable
{
	private static final long serialVersionUID = 1;

	public static final byte SUCCESS_CREATED = CommandResults.CODE_NORMAL;
	public static final byte ERROR_REGION_NOT_CREATED = 1;
	public static final byte ERROR_REGION_ALREADY_EXIST = 2;
	public static final byte ERROR_REGION_PARENT_DOES_NOT_EXIST = 3;
	public static final byte ERROR_REGION_INVALID_PATH = 4;

	private String regionFullPath;
	private RegionAttributeInfo attrInfo;

	public RegionCreateTask()
	{
	}

	/**
	 * Constructs a RegionCreateAllTask object using the default region attribute settings.
	 * @param regionFullPath The region path.
	 */
	public RegionCreateTask(String regionFullPath)
	{
		this(regionFullPath, null);
	}

	/**
	 * Constructs a RegionCreateAllTask object using the specified region attributes.
	 * @param regionFullPath The region path.
	 * @param attrInfo The region attributes. The attribute values are same as the cache.dtd values.
	 */
	public RegionCreateTask(String regionFullPath, RegionAttributeInfo attrInfo)
	{
		this.regionFullPath = regionFullPath;
		this.attrInfo = attrInfo;
	}
	
	/**
	 * Sets the full path of the region to be created.
	 * @param regionFullPath The region full path.
	 */
	public void setRegionPath(String regionFullPath)
	{
		this.regionFullPath = regionFullPath;
	}

	/**
	 * Returns the full path of the region.
	 */
	public String getRegionPath()
	{
		return regionFullPath;
	}
	
	public CommandResults runTask(Object userData)
	{
		return createRegion();
	}

	/**
	 * Creates a region local to this JVM using the region path returned by {@link #getRegionPath()}.
	 */
	@SuppressWarnings({ "rawtypes", "deprecation" })
	private CommandResults createRegion()
	{
		CommandResults results = new CommandResults();
		results.setCode(SUCCESS_CREATED);

		Cache cache = CacheFactory.getAnyInstance();
		MemberInfo memberInfo = new MemberInfo();
		DistributedMember member = cache.getDistributedSystem().getDistributedMember();
		memberInfo.setHost(member.getHost());
		memberInfo.setMemberId(member.getId());
		memberInfo.setMemberName(cache.getName());
		memberInfo.setPid(member.getProcessId());
		results.setDataObject(memberInfo);

		if (regionFullPath == null) {
			results.setCode(ERROR_REGION_INVALID_PATH);
			results.setCodeMessage("Invalid region path: " + regionFullPath);
			return results;
		}
		int index = regionFullPath.lastIndexOf("/");
		if (index == regionFullPath.length() - 1) {
			results.setCode(ERROR_REGION_INVALID_PATH);
			results.setCodeMessage("Invalid region path: " + regionFullPath);
			return results;
		}

		String regionName = regionFullPath.substring(index + 1);
		try {

			Region region = cache.getRegion(regionFullPath);
			if (region != null) {
				results.setCode(ERROR_REGION_ALREADY_EXIST);
				results.setCodeMessage("Region already exist: " + regionFullPath);
			} else {
				Region parentRegion = RegionUtil.getParentRegion(regionFullPath);
				if (parentRegion == null) {
					if (regionFullPath.split("/").length > 2) {
						results.setCode(ERROR_REGION_PARENT_DOES_NOT_EXIST);
						results.setCodeMessage("Parent region does not exist: " + regionFullPath);
					} else {
						if (attrInfo == null) {
							region = cache.createRegion(regionName, new AttributesFactory().create());
						} else {
							region = cache.createRegion(regionName, attrInfo.createRegionAttributes());
						}
						if (region == null) {
							results.setCode(ERROR_REGION_NOT_CREATED);
							results.setCodeMessage("Unable create region: " + regionFullPath);
						} else {
							results.setCodeMessage("Region created: " + region.getFullPath());
						}
					}
				} else {
					if (attrInfo == null) {
						region = parentRegion.createSubregion(regionName, new AttributesFactory().create());
					} else {
						region = parentRegion.createSubregion(regionName, attrInfo.createRegionAttributes());
					}
					if (region == null) {
						results.setCode(ERROR_REGION_NOT_CREATED);
						results.setCodeMessage("Unable create region: " + regionFullPath);
					} else {
						results.setCodeMessage("Region created: " + region.getFullPath());
					}
				}
			}
		} catch (CacheException ex) {
			results.setCode(ERROR_REGION_NOT_CREATED);
			results.setException(ex);
		}
		return results;
	}

	public void fromData(DataInput input) throws IOException, ClassNotFoundException
	{
		regionFullPath = DataSerializerEx.readUTF(input);
		attrInfo = (RegionAttributeInfo) DataSerializer.readObject(input);
	}

	public void toData(DataOutput output) throws IOException
	{
		DataSerializerEx.writeUTF(regionFullPath, output);
		DataSerializer.writeObject(attrInfo, output);
	}

}
